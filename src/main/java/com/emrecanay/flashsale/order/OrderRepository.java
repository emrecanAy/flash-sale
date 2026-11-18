package com.emrecanay.flashsale.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    /**
     * Loads the order and holds its row lock until the transaction ends. Every state change goes
     * through here, so "pay" racing "cancel" or the sweeper is decided by whoever gets the lock
     * first, and the loser sees the final state instead of acting on a stale one. Only the owner's
     * order is found, so another customer's id never takes the lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockByIdAndCustomerId(UUID id, UUID customerId);

    /**
     * Claims a batch of overdue reservations. {@code SKIP LOCKED} lets several application
     * instances sweep at the same time without waiting on, or double-processing, each other's rows.
     */
    @Query(value = """
            SELECT *
              FROM orders
             WHERE status = 'PENDING'
               AND expires_at <= :now
             ORDER BY expires_at
             LIMIT :batchSize
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Order> lockOverdue(Instant now, int batchSize);
}
