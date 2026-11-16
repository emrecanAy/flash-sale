package com.emrecanay.flashsale.order;

import java.util.UUID;

import com.emrecanay.flashsale.shared.DomainException;

class OrderNotPendingException extends DomainException {

    OrderNotPendingException(UUID orderId, OrderStatus currentStatus) {
        super(Kind.CONFLICT, "order_not_pending",
                "Order %s is %s and can no longer be changed.".formatted(orderId, currentStatus));
    }
}
