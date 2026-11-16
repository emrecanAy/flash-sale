package com.emrecanay.flashsale.support;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Creates products and reads stock straight from the database, bypassing the code under test. */
public class TestProducts {

    private final JdbcClient jdbc;

    TestProducts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long createWithStock(int stock) {
        return jdbc.sql("""
                        INSERT INTO products (sku, name, price, available_stock, created_at)
                        VALUES (:sku, 'Test product', 100.00, :stock, now())
                        RETURNING id
                        """)
                .param("sku", "TEST-" + UUID.randomUUID())
                .param("stock", stock)
                .query(Long.class)
                .single();
    }

    public int stockOf(long productId) {
        return jdbc.sql("SELECT available_stock FROM products WHERE id = :id")
                .param("id", productId)
                .query(Integer.class)
                .single();
    }

    /** Units of the product held by orders that are still pending or already paid. */
    public int unitsHeldByOrders(long productId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(l.quantity), 0)
                          FROM order_lines l
                          JOIN orders o ON o.id = l.order_id
                         WHERE l.product_id = :id
                           AND o.status IN ('PENDING', 'PAID')
                        """)
                .param("id", productId)
                .query(Integer.class)
                .single();
    }
}
