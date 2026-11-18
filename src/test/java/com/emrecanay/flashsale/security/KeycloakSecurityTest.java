package com.emrecanay.flashsale.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.emrecanay.flashsale.support.IntegrationTest;
import com.emrecanay.flashsale.support.TestProducts;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * End to end with real tokens: Keycloak runs with the same realm file as {@code compose.yaml}, and
 * the service verifies signatures, issuer and audience against it.
 *
 * <p>The realm's own client only allows the browser flow, so the test registers two clients of its
 * own through the admin API: one that may use the password grant, and one that issues tokens for a
 * different API.
 */
@IntegrationTest
class KeycloakSecurityTest {

    private static final String PASSWORD = "flash-sale-demo-2026";

    @SuppressWarnings("resource")
    static final GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.4")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(MountableFile.forHostPath("deploy/keycloak/flash-sale-realm.json"),
                    "/opt/keycloak/data/import/flash-sale-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/flash-sale/.well-known/openid-configuration")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        keycloak.start();
    }

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> baseUrl() + "/realms/flash-sale");
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestProducts products;

    @BeforeAll
    static void registerTestClients() {
        String admin = token("master", "admin-cli", "admin", "admin");
        createClient(admin, "test-runner", true);
        createClient(admin, "other-api-client", false);
    }

    @Test
    void aCustomerOrdersWithARealTokenAndTheOrderIsStoredUnderTheirAccountId() {
        String alice = customerToken("alice@flash-sale.local");

        MvcTestResult result = placeOrder(alice, products.createWithStock(1));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.customerId").isEqualTo(subjectOf(alice));
    }

    @Test
    void realmRolesDecideWhoManagesTheCatalogue() {
        String body = """
                {"sku": "SKU-%s", "name": "Limited sneaker", "price": 1499.90, "initialStock": 3}
                """.formatted(UUID.randomUUID());

        assertThat(createProduct(customerToken("alice@flash-sale.local"), body)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(createProduct(customerToken("mira@flash-sale.local"), body)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void oneCustomerCannotReadAnothersOrder() throws Exception {
        MvcTestResult placed = placeOrder(customerToken("alice@flash-sale.local"), products.createWithStock(1));
        String orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.id");

        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken("bob@flash-sale.local")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    /** Signed by the right realm, but issued for another API: the audience check turns it away. */
    @Test
    void aTokenIssuedForAnotherApiIsRejected() {
        String foreign = token("flash-sale", "other-api-client", "alice@flash-sale.local", PASSWORD);

        MvcTestResult result = placeOrder(foreign, products.createWithStock(1));

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("unauthorized");
    }

    @Test
    void aTamperedTokenIsRejected() {
        String alice = customerToken("alice@flash-sale.local");
        String[] parts = alice.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"customer\"", "\"catalog-manager\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThat(createProduct(forged, """
                {"sku": "SKU-%s", "name": "Forged", "price": 1.00, "initialStock": 1}
                """.formatted(UUID.randomUUID()))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    /** New accounts, whether created by an admin or signed up on the login page, can shop right away. */
    @Test
    void aNewAccountGetsTheCustomerRoleByDefault() {
        String email = "new-" + UUID.randomUUID() + "@flash-sale.local";
        createUser(token("master", "admin-cli", "admin", "admin"), email);

        assertThat(placeOrder(token("flash-sale", "test-runner", email, PASSWORD), products.createWithStock(1)))
                .hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult placeOrder(String accessToken, long productId) {
        return mvc.post().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"items": [{"productId": %d, "quantity": 1}]}
                        """.formatted(productId))
                .exchange();
    }

    private MvcTestResult createProduct(String accessToken, String body) {
        return mvc.post().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String customerToken(String username) {
        return token("flash-sale", "test-runner", username, PASSWORD);
    }

    private static String subjectOf(String accessToken) {
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.sub");
    }

    private static String token(String realm, String clientId, String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", username);
        form.add("password", password);
        Map<?, ?> response = keycloakClient().post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return (String) response.get("access_token");
    }

    private static void createClient(String adminToken, String clientId, boolean forThisApi) {
        Map<String, Object> client = new HashMap<>(Map.of(
                "clientId", clientId,
                "publicClient", true,
                "standardFlowEnabled", false,
                "directAccessGrantsEnabled", true));
        if (forThisApi) {
            client.put("protocolMappers", List.of(Map.of(
                    "name", "flash-sale-api audience",
                    "protocol", "openid-connect",
                    "protocolMapper", "oidc-audience-mapper",
                    "config", Map.of(
                            "included.custom.audience", "flash-sale-api",
                            "access.token.claim", "true",
                            "id.token.claim", "false"))));
        }
        keycloakClient().post()
                .uri("/admin/realms/flash-sale/clients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(client)
                .retrieve()
                .toBodilessEntity();
    }

    private static void createUser(String adminToken, String email) {
        keycloakClient().post()
                .uri("/admin/realms/flash-sale/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", email,
                        "email", email,
                        "firstName", "New",
                        "lastName", "Customer",
                        "enabled", true,
                        "emailVerified", true,
                        "credentials", List.of(Map.of("type", "password", "value", PASSWORD, "temporary", false))))
                .retrieve()
                .toBodilessEntity();
    }

    private static RestClient keycloakClient() {
        return RestClient.builder().baseUrl(baseUrl()).build();
    }

    private static String baseUrl() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }
}
