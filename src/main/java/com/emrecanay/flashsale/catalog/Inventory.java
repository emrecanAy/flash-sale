package com.emrecanay.flashsale.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of {@code products.available_stock}.
 *
 * <p>Every change is a single conditional {@code UPDATE}. The database evaluates the stock check
 * and the decrement under one row lock, so two buyers can never both see "1 left" and both win.
 * There is no read-modify-write cycle to protect, hence no version column and no retry loop.
 *
 * <p>Reserving and releasing join the caller's transaction on purpose: stock must move back
 * automatically if the order that asked for it fails to commit.
 */
@Service
public class Inventory {

    private final JdbcClient jdbc;

    Inventory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Takes the requested quantities out of stock, all or nothing.
     *
     * @return what was reserved, with the unit price at the moment of reservation
     * @throws InsufficientStockException if any product cannot cover its quantity
     * @throws ProductNotFoundException   if any product does not exist
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ReservedItem> reserve(Map<Long, Integer> quantitiesByProductId) {
        List<ReservedItem> reserved = new ArrayList<>(quantitiesByProductId.size());
        inLockOrder(quantitiesByProductId)
                .forEach((productId, quantity) -> reserved.add(reserveOne(productId, quantity)));
        return reserved;
    }

    /** Puts previously reserved quantities back, e.g. when an order is cancelled or expires. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Map<Long, Integer> quantitiesByProductId) {
        inLockOrder(quantitiesByProductId).forEach(this::addStock);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void restock(long productId, int quantity) {
        if (addStock(productId, quantity) == 0) {
            throw new ProductNotFoundException(productId);
        }
    }

    private ReservedItem reserveOne(long productId, int quantity) {
        return jdbc.sql("""
                        UPDATE products
                           SET available_stock = available_stock - :quantity
                         WHERE id = :productId
                           AND available_stock >= :quantity
                        RETURNING price
                        """)
                .param("productId", productId)
                .param("quantity", quantity)
                .query(BigDecimal.class)
                .optional()
                .map(unitPrice -> new ReservedItem(productId, quantity, unitPrice))
                .orElseThrow(() -> exists(productId)
                        ? new InsufficientStockException(productId, quantity)
                        : new ProductNotFoundException(productId));
    }

    private int addStock(long productId, int quantity) {
        return jdbc.sql("UPDATE products SET available_stock = available_stock + :quantity WHERE id = :productId")
                .param("productId", productId)
                .param("quantity", quantity)
                .update();
    }

    private boolean exists(long productId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM products WHERE id = :productId)")
                .param("productId", productId)
                .query(Boolean.class)
                .single();
    }

    /**
     * Row locks are taken in ascending product id order by every transaction. Without a global
     * order, one order for (A, B) and another for (B, A) could each hold one row and wait
     * forever for the other.
     */
    private static Map<Long, Integer> inLockOrder(Map<Long, Integer> quantitiesByProductId) {
        return new TreeMap<>(quantitiesByProductId);
    }
}
