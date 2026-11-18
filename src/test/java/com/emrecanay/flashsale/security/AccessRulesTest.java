package com.emrecanay.flashsale.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.emrecanay.flashsale.support.IntegrationTest;
import com.emrecanay.flashsale.support.TestProducts;
import com.emrecanay.flashsale.support.Tokens;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Who may call what. Token signatures are covered by {@link KeycloakSecurityTest}. */
@IntegrationTest
class AccessRulesTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestProducts products;

    @Test
    void theCatalogueIsOpenToEveryone() {
        assertThat(mvc.get().uri("/api/v1/products")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/products/{id}", products.createWithStock(1))).hasStatusOk();
    }

    @Test
    void orderingWithoutATokenIsUnauthorizedAndSaysHowToAuthenticate() {
        MvcTestResult result = mvc.post().uri("/api/v1/orders")
                .header("Idempotency-Key", "anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderFor(products.createWithStock(1)))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("unauthorized");
        // RFC 9728: the challenge points clients at the metadata that names the authorization server.
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer")
                .contains("resource_metadata=");
    }

    @Test
    void theProtectedResourceMetadataNamesKeycloakAsTheAuthorizationServer() {
        assertThat(mvc.get().uri("/.well-known/oauth-protected-resource"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.authorization_servers[0]")
                .isEqualTo("http://localhost:1/realms/flash-sale");
    }

    @Test
    void customersCannotManageTheCatalogue() {
        MvcTestResult result = mvc.post().uri("/api/v1/products")
                .with(Tokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku": "SKU-%s", "name": "Not mine to add", "price": 1.00, "initialStock": 1}
                        """.formatted(UUID.randomUUID()))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("forbidden");
        assertThat(mvc.post().uri("/api/v1/products/{id}/restock", products.createWithStock(1))
                .with(Tokens.customer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantity": 5}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAccountWithoutTheCustomerRoleCannotOrder() {
        assertThat(placeOrder(Tokens.catalogManager(), "staff", orderFor(products.createWithStock(1))))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    /** The order belongs to the token's subject; a customer id smuggled into the body is ignored. */
    @Test
    void theOrderIsPlacedForTheTokensSubjectWhateverTheBodySays() {
        UUID me = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        long productId = products.createWithStock(1);

        MvcTestResult result = placeOrder(Tokens.customer(me), "smuggled", """
                {"customerId": "%s", "items": [{"productId": %d, "quantity": 1}]}
                """.formatted(someoneElse, productId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.customerId").isEqualTo(me.toString());
    }

    @Test
    void anotherCustomersOrderLooksLikeItDoesNotExist() throws Exception {
        long productId = products.createWithStock(2);
        RequestPostProcessor owner = Tokens.customer();
        RequestPostProcessor stranger = Tokens.customer();
        MvcTestResult placed = placeOrder(owner, "mine", orderFor(productId));
        String orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.id");

        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId).with(stranger))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("order_not_found");
        assertThat(mvc.post().uri("/api/v1/orders/{id}/payment", orderId).with(stranger))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/api/v1/orders/{id}/cancellation", orderId).with(stranger))
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId).with(owner))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        assertThat(products.stockOf(productId)).isEqualTo(1);
    }

    /** Two customers may pick the same Idempotency-Key; each gets their own order. */
    @Test
    void idempotencyKeysArePerCustomer() throws Exception {
        long productId = products.createWithStock(2);

        MvcTestResult first = placeOrder(Tokens.customer(), "checkout", orderFor(productId));
        MvcTestResult second = placeOrder(Tokens.customer(), "checkout", orderFor(productId));

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(JsonPath.<String>read(second.getResponse().getContentAsString(), "$.id"))
                .isNotEqualTo(JsonPath.read(first.getResponse().getContentAsString(), "$.id"));
    }

    @Test
    void healthIsPublicButMetricsNeedTheOperatorRole() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/metrics")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/metrics").with(Tokens.customer())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/actuator/metrics").with(Tokens.operator())).hasStatusOk();
    }

    @Test
    void theApiDescriptionIsPublicAndAdvertisesTheSignInFlow() {
        MvcTestResult docs = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(docs).hasStatusOk();
        assertThat(docs).bodyJson()
                .extractingPath("$.components.securitySchemes.keycloak.flows.authorizationCode.tokenUrl")
                .asString().endsWith("/realms/flash-sale/protocol/openid-connect/token");
    }

    @Test
    void pathsWithoutARuleAreClosed() {
        assertThat(mvc.get().uri("/api/v1/anything-new")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/anything-new").with(Tokens.as(UUID.randomUUID(),
                Roles.CUSTOMER, Roles.CATALOG_MANAGER, Roles.OPERATOR))).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult placeOrder(RequestPostProcessor token, String idempotencyKey, String body) {
        return mvc.post().uri("/api/v1/orders")
                .with(token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String orderFor(long productId) {
        return """
                {"items": [{"productId": %d, "quantity": 1}]}
                """.formatted(productId);
    }
}
