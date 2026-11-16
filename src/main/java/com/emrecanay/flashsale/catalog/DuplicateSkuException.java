package com.emrecanay.flashsale.catalog;

import com.emrecanay.flashsale.shared.DomainException;

class DuplicateSkuException extends DomainException {

    DuplicateSkuException(String sku) {
        super(Kind.CONFLICT, "duplicate_sku", "A product with SKU '%s' already exists.".formatted(sku));
    }
}
