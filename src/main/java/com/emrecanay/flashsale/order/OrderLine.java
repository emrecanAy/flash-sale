package com.emrecanay.flashsale.order;

import java.math.BigDecimal;

import jakarta.persistence.Embeddable;

/** The unit price is a snapshot: later price changes must not rewrite what the customer agreed to pay. */
@Embeddable
record OrderLine(long productId, int quantity, BigDecimal unitPrice) {

    BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
