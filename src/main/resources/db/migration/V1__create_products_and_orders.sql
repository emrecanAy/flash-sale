CREATE TABLE products (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku             VARCHAR(64)    NOT NULL,
    name            VARCHAR(200)   NOT NULL,
    price           NUMERIC(12, 2) NOT NULL,
    available_stock INTEGER        NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price_not_negative CHECK (price >= 0),
    -- Last line of defence: even a buggy writer cannot oversell.
    CONSTRAINT ck_products_stock_not_negative CHECK (available_stock >= 0)
);

CREATE TABLE orders (
    id              UUID           PRIMARY KEY,
    customer_id     UUID           NOT NULL,
    idempotency_key VARCHAR(64)    NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    total_amount    NUMERIC(14, 2) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    expires_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_orders_customer_idempotency_key UNIQUE (customer_id, idempotency_key),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED'))
);

-- The sweeper only ever looks at unpaid orders, so the index stays small no matter how many orders pile up.
CREATE INDEX ix_orders_pending_expiry ON orders (expires_at) WHERE status = 'PENDING';

CREATE TABLE order_lines (
    order_id   UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id BIGINT         NOT NULL REFERENCES products (id),
    quantity   INTEGER        NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (order_id, product_id),
    CONSTRAINT ck_order_lines_quantity_positive CHECK (quantity > 0)
);
