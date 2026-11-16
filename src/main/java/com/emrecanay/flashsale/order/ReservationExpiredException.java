package com.emrecanay.flashsale.order;

import java.util.UUID;

import com.emrecanay.flashsale.shared.DomainException;

class ReservationExpiredException extends DomainException {

    ReservationExpiredException(UUID orderId) {
        super(Kind.CONFLICT, "reservation_expired",
                "The reservation for order %s has expired. Place a new order.".formatted(orderId));
    }
}
