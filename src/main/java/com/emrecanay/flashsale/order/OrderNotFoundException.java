package com.emrecanay.flashsale.order;

import java.util.UUID;

import com.emrecanay.flashsale.shared.DomainException;

class OrderNotFoundException extends DomainException {

    OrderNotFoundException(UUID orderId) {
        super(Kind.NOT_FOUND, "order_not_found", "Order %s does not exist.".formatted(orderId));
    }
}
