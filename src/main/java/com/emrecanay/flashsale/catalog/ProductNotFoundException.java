package com.emrecanay.flashsale.catalog;

import com.emrecanay.flashsale.shared.DomainException;

public class ProductNotFoundException extends DomainException {

    ProductNotFoundException(long productId) {
        super(Kind.NOT_FOUND, "product_not_found", "Product %d does not exist.".formatted(productId));
    }
}
