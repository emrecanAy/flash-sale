package com.emrecanay.flashsale.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ProductCatalog {

    private final ProductRepository products;
    private final Inventory inventory;

    ProductCatalog(ProductRepository products, Inventory inventory) {
        this.products = products;
        this.inventory = inventory;
    }

    Page<ProductResponse> list(Pageable pageable) {
        return products.findAll(pageable).map(ProductResponse::from);
    }

    ProductResponse get(long productId) {
        return products.findById(productId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    ProductResponse create(CreateProductRequest request) {
        // Friendly error for the common case; uq_products_sku still decides when two creates race.
        if (products.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }
        Product product = new Product(request.sku(), request.name(), request.price(), request.initialStock());
        return ProductResponse.from(products.save(product));
    }

    @Transactional
    ProductResponse restock(long productId, int quantity) {
        inventory.restock(productId, quantity);
        return get(productId);
    }
}
