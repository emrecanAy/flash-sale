package com.emrecanay.flashsale.catalog;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "products")
class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;

    private String name;

    private BigDecimal price;

    /**
     * Written once on insert and never again by Hibernate. Stock only moves through the atomic
     * statements in {@link Inventory}; if this column were part of entity updates, saving a
     * product that was loaded a moment ago would silently overwrite sales made in between.
     */
    @Column(updatable = false)
    private int availableStock;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected Product() {
    }

    Product(String sku, String name, BigDecimal price, int initialStock) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.availableStock = initialStock;
    }

    Long getId() {
        return id;
    }

    String getSku() {
        return sku;
    }

    String getName() {
        return name;
    }

    BigDecimal getPrice() {
        return price;
    }

    int getAvailableStock() {
        return availableStock;
    }
}
