package com.emrecanay.flashsale.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);
}
