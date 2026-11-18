package com.emrecanay.flashsale.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns Keycloak's {@code realm_access.roles} claim into {@code ROLE_*} authorities, so access rules
 * can use {@code hasRole(Roles.CUSTOMER)}. Spring's default converter only reads {@code scope}.
 */
public final class RealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String CLAIM = "realm_access";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM);
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
