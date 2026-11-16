# flash-sale

[![CI](https://github.com/emrecanAy/flash-sale/actions/workflows/ci.yml/badge.svg)](https://github.com/emrecanAy/flash-sale/actions/workflows/ci.yml)

An order service for limited-stock product drops: a few units, far more buyers, everyone clicking at the same second.

The interesting part of such a system is not the CRUD, it is what happens when requests overlap. This project is a small, runnable Spring Boot service that gets those overlaps right and proves it with tests against a real PostgreSQL.

**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL 18 · Flyway · Spring Data JPA + `JdbcClient` · Testcontainers · Spring Modulith (boundary checks)

## What can go wrong, and what prevents it

| Race | What prevents it | Proven by |
|---|---|---|
| Two buyers, one unit left: both read "1 in stock", both order | Stock is decremented by one conditional `UPDATE … WHERE available_stock >= :qty`. Check and write happen under a single row lock; there is no read-then-write window. A `CHECK (available_stock >= 0)` constraint backs it up. | `OversellingTest` – 200 concurrent buyers, 10 units, exactly 10 orders |
| Multi-item order fails halfway and keeps part of the stock | Reservation runs inside the order's transaction (`Propagation.MANDATORY`), so it commits or rolls back with the order | `OversellingTest` |
| Order (A, B) and order (B, A) deadlock each other | Product rows are always locked in ascending id order, also when a batch of expired orders is released | `OversellingTest` |
| Client times out and retries, or the user double-clicks "Buy" | `Idempotency-Key` header, enforced by a unique index on `(customer_id, idempotency_key)`. A duplicate returns the original order with `200` instead of creating a second one. | `IdempotencyTest` – 20 concurrent duplicates for the last unit all get the same order |
| Cancel is sent twice, or races a payment: stock is returned twice | State changes load the order with `SELECT … FOR UPDATE`; the second caller waits, then sees the final state | `OrderLifecycleTest` |
| Abandoned checkouts hold stock forever | Orders reserve stock for 15 minutes. A sweeper expires unpaid ones and returns their stock, claiming rows with `FOR UPDATE SKIP LOCKED` so several instances can run it at once | `OrderLifecycleTest` |

## Run it

Requirements: JDK 21+ and Docker.

```bash
./mvnw spring-boot:run
```

That starts PostgreSQL from `compose.yaml`, applies the Flyway migrations and loads three demo products.

- Swagger UI: http://localhost:8080/swagger-ui.html
- Ready-made requests: [`api.http`](api.http)
- Health: http://localhost:8080/actuator/health

```bash
./mvnw verify   # unit + integration tests; Testcontainers starts its own PostgreSQL
```

To run the packaged jar against your own database, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`.

## API

| Method | Path | |
|---|---|---|
| `GET` | `/api/v1/products` | Paged catalogue with current stock |
| `POST` | `/api/v1/products` | Create a product with its initial stock |
| `POST` | `/api/v1/products/{id}/restock` | Add stock |
| `POST` | `/api/v1/orders` | Place an order and reserve stock. Requires `Idempotency-Key`. `201` when created, `200` when replayed |
| `GET` | `/api/v1/orders/{id}` | Order details |
| `POST` | `/api/v1/orders/{id}/payment` | Confirm a pending order (payment itself is simulated) |
| `POST` | `/api/v1/orders/{id}/cancellation` | Cancel a pending order and return its stock |

```bash
curl -i -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: checkout-001' \
  -d '{"customerId": "7f1c1c58-6a55-4c5e-9d5b-2f6f0a6f4b11", "items": [{"productId": 3, "quantity": 1}]}'
```

Errors are RFC 9457 problem documents with a stable `code`:

```json
{
  "status": 409,
  "title": "Conflict",
  "detail": "Product 3 does not have 1 unit(s) in stock.",
  "instance": "/api/v1/orders",
  "code": "insufficient_stock"
}
```

| Status | `code` |
|---|---|
| 400 | `validation_failed` |
| 404 | `product_not_found`, `order_not_found` |
| 409 | `insufficient_stock`, `order_not_pending`, `reservation_expired`, `duplicate_sku` |
| 422 | `idempotency_key_reused` (same key, different items) |
| 503 | `service_busy` (with `Retry-After`) |

## Code layout

A modular monolith, packaged by feature. Classes are package-private unless another module needs them.

```
com.emrecanay.flashsale
├── catalog     products and stock
│   └── Inventory          public API: reserve / release, the only writer of stock
├── order       placing, paying, cancelling, expiring
│   ├── OrderPlacement     idempotent order creation
│   ├── OrderLifecycle     state changes under a row lock
│   └── ReservationSweeper scheduled expiry
└── shared      DomainException + the single place that turns exceptions into HTTP
```

`order` depends on `catalog` only through `Inventory`; `ModularityTest` fails the build if a cycle or a shortcut appears.

## Design notes

**Why a conditional `UPDATE` instead of `@Version`.** Optimistic locking is the usual answer to lost updates, but it is the wrong tool for a hot counter. With 200 buyers on one row, 199 transactions would fail on the version check and have to retry, most of them more than once. The conditional update asks the database the only question that matters ("is there enough?") and gets the answer and the write in one round trip, with nothing to retry.

**Why stock is not an updatable entity field.** `Product.availableStock` is mapped with `updatable = false`. If Hibernate wrote it back, renaming a product from an entity loaded a second ago would overwrite every sale made since.

**Why pessimistic locks for order state.** Contention on a single order is rare and short, and a row lock gives the loser a precise answer ("already cancelled") rather than a generic "try again".

**Stock first, order second.** When a drop sells out, nearly all requests are doomed. They end after one `UPDATE` that matched no row, before anything is inserted.

**Virtual threads and back-pressure.** Request handling runs on virtual threads, so the servlet thread pool no longer limits concurrency; the database connection pool does. HikariCP is capped at 20 connections with a 2-second acquisition timeout, and a request that cannot get one is answered with `503` and `Retry-After` instead of piling up.

**No Redis, no queue.** PostgreSQL row locks are enough for correctness and keep the system one process and one database. A cache or a queue in front becomes interesting when the write rate outgrows a single primary; that is a scaling decision, not a correctness one.

## Deliberately left out

- Authentication. `customerId` is taken from the request body; in a real deployment it would come from the access token.
- A real payment provider. `/payment` flips the state; the concurrency rules around it are the point.
- Per-client rate limiting, which belongs at the gateway.

## License

MIT
