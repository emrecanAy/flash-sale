package com.emrecanay.flashsale.order;

import com.emrecanay.flashsale.shared.DomainException;

class IdempotencyKeyReusedException extends DomainException {

    IdempotencyKeyReusedException(String idempotencyKey) {
        super(Kind.UNPROCESSABLE, "idempotency_key_reused",
                "Idempotency key '%s' was already used for an order with different items.".formatted(idempotencyKey));
    }
}
