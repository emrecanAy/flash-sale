package com.emrecanay.flashsale.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant expiresAt,
        List<Item> items) {

    record Item(long productId, int quantity, BigDecimal unitPrice) {
    }

    static OrderResponse from(Order order) {
        List<Item> items = order.getLines().stream()
                .map(line -> new Item(line.productId(), line.quantity(), line.unitPrice()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(), order.getTotalAmount(),
                order.getCreatedAt(), order.getExpiresAt(), items);
    }
}
