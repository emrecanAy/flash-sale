package com.emrecanay.flashsale.catalog;

import com.emrecanay.flashsale.shared.DomainException;

public class InsufficientStockException extends DomainException {

    InsufficientStockException(long productId, int requestedQuantity) {
        super(Kind.CONFLICT, "insufficient_stock",
                "Product %d does not have %d unit(s) in stock.".formatted(productId, requestedQuantity));
    }
}
