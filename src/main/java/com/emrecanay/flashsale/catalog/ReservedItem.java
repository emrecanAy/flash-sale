package com.emrecanay.flashsale.catalog;

import java.math.BigDecimal;

public record ReservedItem(long productId, int quantity, BigDecimal unitPrice) {
}
