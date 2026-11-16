package com.emrecanay.flashsale.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.emrecanay.flashsale.support.IntegrationTest;
import com.emrecanay.flashsale.support.TestProducts;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class OrderApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestProducts products;

    @Test
    void anOrderIsPlacedReplayedAndPaidOverHttp() throws Exception {
        long productId = products.createWithStock(3);
        String body = orderFor(productId, 2);

        MvcTestResult created = placeOrder("key-1", body);
        assertThat(created).hasStatus(HttpStatus.CREATED).containsHeader("Location");
        assertThat(created).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        assertThat(created).bodyJson().extractingPath("$.totalAmount").isEqualTo(200.00);
        String orderId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        MvcTestResult replayed = placeOrder("key-1", body);
        assertThat(replayed).hasStatusOk();
        assertThat(replayed).bodyJson().extractingPath("$.id").isEqualTo(orderId);

        assertThat(mvc.post().uri("/api/v1/orders/{id}/payment", orderId))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("PAID");
        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId))
                .hasStatusOk()
                .bodyJson().extractingPath("$.items[0].quantity").isEqualTo(2);
    }

    @Test
    void sellingOutIsReportedAsAConflictProblem() {
        long productId = products.createWithStock(1);

        MvcTestResult result = placeOrder("key-2", orderFor(productId, 2));

        assertThat(result).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("insufficient_stock");
    }

    @Test
    void reusingAnIdempotencyKeyForADifferentOrderIsUnprocessable() {
        long productId = products.createWithStock(5);
        UUID customerId = UUID.randomUUID();
        placeOrder("key-3", orderFor(customerId, productId, 1));

        MvcTestResult result = placeOrder("key-3", orderFor(customerId, productId, 2));

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("idempotency_key_reused");
    }

    @Test
    void invalidRequestsAreRejectedWithFieldLevelDetails() {
        MvcTestResult result = placeOrder("key-4", """
                {"customerId": null, "items": []}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().hasPath("$.errors.customerId").hasPath("$.errors.items");
    }

    @Test
    void aBlankIdempotencyKeyIsRejected() {
        MvcTestResult result = placeOrder(" ", orderFor(1, 1));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().hasPath("$.errors.idempotencyKey");
    }

    @Test
    void theIdempotencyKeyHeaderIsMandatory() {
        assertThat(mvc.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderFor(1, 1)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownOrdersAreNotFound() {
        assertThat(mvc.get().uri("/api/v1/orders/{id}", UUID.randomUUID()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("order_not_found");
    }

    private MvcTestResult placeOrder(String idempotencyKey, String body) {
        return mvc.post().uri("/api/v1/orders")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String orderFor(long productId, int quantity) {
        return orderFor(UUID.randomUUID(), productId, quantity);
    }

    private static String orderFor(UUID customerId, long productId, int quantity) {
        return """
                {"customerId": "%s", "items": [{"productId": %d, "quantity": %d}]}
                """.formatted(customerId, productId, quantity);
    }
}
