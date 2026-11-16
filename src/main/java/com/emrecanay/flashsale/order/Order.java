package com.emrecanay.flashsale.order;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "orders")
class Order {

    // Time-ordered UUIDs keep primary key inserts append-only, unlike random v4 ids.
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID customerId;

    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal totalAmount;

    private Instant createdAt;

    private Instant expiresAt;

    // An order is never useful without its handful of lines, so they are always loaded with it.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @BatchSize(size = 100)
    private List<OrderLine> lines = new ArrayList<>();

    protected Order() {
    }

    private Order(UUID customerId, String idempotencyKey, List<OrderLine> lines, Instant now, Duration reservationTtl) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.lines.addAll(lines);
        this.totalAmount = lines.stream().map(OrderLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.PENDING;
        // PostgreSQL keeps microseconds; truncating here makes a fresh order equal to the one read back later.
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.expiresAt = createdAt.plus(reservationTtl);
    }

    static Order place(UUID customerId, String idempotencyKey, List<OrderLine> lines, Instant now,
            Duration reservationTtl) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line.");
        }
        return new Order(customerId, idempotencyKey, lines, now, reservationTtl);
    }

    void pay(Instant now) {
        requirePending();
        // The sweeper runs periodically, so an order can be overdue and still PENDING for a moment.
        if (!now.isBefore(expiresAt)) {
            throw new ReservationExpiredException(id);
        }
        status = OrderStatus.PAID;
    }

    void cancel() {
        requirePending();
        status = OrderStatus.CANCELLED;
    }

    void expire() {
        requirePending();
        status = OrderStatus.EXPIRED;
    }

    private void requirePending() {
        if (status != OrderStatus.PENDING) {
            throw new OrderNotPendingException(id, status);
        }
    }

    Map<Long, Integer> quantitiesByProductId() {
        return lines.stream().collect(Collectors.toMap(OrderLine::productId, OrderLine::quantity));
    }

    UUID getId() {
        return id;
    }

    UUID getCustomerId() {
        return customerId;
    }

    OrderStatus getStatus() {
        return status;
    }

    BigDecimal getTotalAmount() {
        return totalAmount;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    List<OrderLine> getLines() {
        return List.copyOf(lines);
    }
}
