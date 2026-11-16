package com.emrecanay.flashsale.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.emrecanay.flashsale.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class ProductApiTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void aProductIsCreatedAndRestocked() throws Exception {
        MvcTestResult created = createProduct("SKU-" + UUID.randomUUID(), 5);
        assertThat(created).hasStatus(HttpStatus.CREATED).containsHeader("Location");
        int productId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        assertThat(mvc.post().uri("/api/v1/products/{id}/restock", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantity": 7}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.availableStock").isEqualTo(12);
    }

    @Test
    void aSkuCanOnlyBeUsedOnce() {
        String sku = "SKU-" + UUID.randomUUID();
        createProduct(sku, 1);

        MvcTestResult duplicate = createProduct(sku, 1);

        assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
        assertThat(duplicate).bodyJson().extractingPath("$.code").isEqualTo("duplicate_sku");
    }

    @Test
    void restockingAnUnknownProductIsNotFound() {
        assertThat(mvc.post().uri("/api/v1/products/{id}/restock", Long.MAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantity": 1}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult createProduct(String sku, int initialStock) {
        return mvc.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku": "%s", "name": "Limited sneaker", "price": 1499.90, "initialStock": %d}
                        """.formatted(sku, initialStock))
                .exchange();
    }
}
