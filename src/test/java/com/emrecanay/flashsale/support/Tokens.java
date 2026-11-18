package com.emrecanay.flashsale.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.emrecanay.flashsale.security.RealmRoleConverter;
import com.emrecanay.flashsale.security.Roles;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Signs MockMvc requests in with a Keycloak-shaped token. Signature checks are skipped (that is
 * what {@code KeycloakSecurityTest} covers with real tokens), but roles still go through the
 * application's own claim converter.
 */
public final class Tokens {

    private Tokens() {
    }

    public static RequestPostProcessor customer(UUID customerId) {
        return as(customerId, Roles.CUSTOMER);
    }

    public static RequestPostProcessor customer() {
        return customer(UUID.randomUUID());
    }

    public static RequestPostProcessor catalogManager() {
        return as(UUID.randomUUID(), Roles.CATALOG_MANAGER);
    }

    public static RequestPostProcessor operator() {
        return as(UUID.randomUUID(), Roles.OPERATOR);
    }

    public static RequestPostProcessor as(UUID subject, String... realmRoles) {
        return jwt()
                .jwt(token -> token
                        .subject(subject.toString())
                        .claim("realm_access", Map.of("roles", List.of(realmRoles))))
                .authorities(new RealmRoleConverter());
    }
}
