package com.emrecanay.flashsale.order;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.emrecanay.flashsale.catalog.InsufficientStockException;
import com.emrecanay.flashsale.catalog.Inventory;
import com.emrecanay.flashsale.catalog.ReservedItem;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Places orders exactly once per {@code (customer, idempotency key)}, however often and however
 * concurrently the client sends the request.
 */
@Service
class OrderPlacement {

    record Result(OrderResponse order, boolean replayed) {
    }

    private final OrderRepository orders;
    private final Inventory inventory;
    private final TransactionTemplate transaction;
    private final ReservationProperties reservation;
    private final Clock clock;

    OrderPlacement(OrderRepository orders, Inventory inventory, TransactionTemplate transaction,
            ReservationProperties reservation, Clock clock) {
        this.orders = orders;
        this.inventory = inventory;
        this.transaction = transaction;
        this.reservation = reservation;
        this.clock = clock;
    }

    Result place(UUID customerId, String idempotencyKey, Map<Long, Integer> quantitiesByProductId) {
        Optional<Result> replay = findReplay(customerId, idempotencyKey, quantitiesByProductId);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            OrderResponse created = transaction.execute(
                    status -> reserveAndCreate(customerId, idempotencyKey, quantitiesByProductId));
            return new Result(created, false);
        }
        catch (InsufficientStockException | DataIntegrityViolationException ex) {
            // Two requests with the same key can both pass the check above. The loser fails here,
            // either on the unique key or because its twin just took the last unit. By now the
            // winner has committed, so a second look tells a duplicate apart from a real failure.
            return findReplay(customerId, idempotencyKey, quantitiesByProductId).orElseThrow(() -> ex);
        }
    }

    private OrderResponse reserveAndCreate(UUID customerId, String idempotencyKey,
            Map<Long, Integer> quantitiesByProductId) {
        // Stock first: when a drop sells out, the vast majority of requests end right here,
        // after one cheap UPDATE that matched no row.
        List<OrderLine> lines = inventory.reserve(quantitiesByProductId).stream()
                .map(OrderPlacement::toLine)
                .toList();
        Order order = Order.place(customerId, idempotencyKey, lines, clock.instant(), reservation.ttl());
        // Flush now so a duplicate idempotency key fails inside this try block, not at commit.
        return OrderResponse.from(orders.saveAndFlush(order));
    }

    private Optional<Result> findReplay(UUID customerId, String idempotencyKey,
            Map<Long, Integer> quantitiesByProductId) {
        return orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey).map(existing -> {
            if (!existing.quantitiesByProductId().equals(quantitiesByProductId)) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            return new Result(OrderResponse.from(existing), true);
        });
    }

    private static OrderLine toLine(ReservedItem item) {
        return new OrderLine(item.productId(), item.quantity(), item.unitPrice());
    }
}
