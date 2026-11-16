package com.emrecanay.flashsale.order;

public enum OrderStatus {
    /** Stock is reserved, payment has not arrived yet. */
    PENDING,
    PAID,
    CANCELLED,
    /** Payment did not arrive in time; the reserved stock went back on sale. */
    EXPIRED
}
