package com.emrecanay.flashsale.order;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.emrecanay.flashsale.catalog.Inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything that happens to an order after it has been placed. */
@Service
class OrderLifecycle {

    private final OrderRepository orders;
    private final Inventory inventory;
    private final ReservationProperties reservation;
    private final Clock clock;

    OrderLifecycle(OrderRepository orders, Inventory inventory, ReservationProperties reservation, Clock clock) {
        this.orders = orders;
        this.inventory = inventory;
        this.reservation = reservation;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    OrderResponse get(UUID orderId) {
        return orders.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Payment itself is simulated; what matters here is that it cannot race a cancel or an expiry. */
    @Transactional
    OrderResponse pay(UUID orderId) {
        Order order = lock(orderId);
        order.pay(clock.instant());
        return OrderResponse.from(order);
    }

    @Transactional
    OrderResponse cancel(UUID orderId) {
        Order order = lock(orderId);
        order.cancel();
        inventory.release(order.quantitiesByProductId());
        return OrderResponse.from(order);
    }

    /**
     * Expires one batch of reservations that were due by {@code now} and puts their stock back on sale.
     *
     * @return how many orders were expired; a full batch means there is probably more to do
     */
    @Transactional
    int expireOverdue(Instant now) {
        List<Order> overdue = orders.lockOverdue(now, reservation.sweepBatchSize());
        Map<Long, Integer> quantitiesToRelease = new HashMap<>();
        for (Order order : overdue) {
            order.expire();
            order.quantitiesByProductId().forEach((productId, quantity) ->
                    quantitiesToRelease.merge(productId, quantity, Integer::sum));
        }
        // One release for the whole batch keeps product rows locked in id order. Releasing order by
        // order would lock them in arbitrary order and could deadlock against customers checking out.
        inventory.release(quantitiesToRelease);
        return overdue.size();
    }

    private Order lock(UUID orderId) {
        return orders.findWithLockById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
