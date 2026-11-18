package com.emrecanay.flashsale.security;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Builds the authentication for a verified access token. The token's subject is the customer id
 * that orders are stored under, so a token whose subject is not a UUID is rejected here with 401
 * rather than failing later inside a request.
 */
final class CustomerTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final RealmRoleConverter roles = new RealmRoleConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!isUuid(jwt.getSubject())) {
            throw new InvalidBearerTokenException("The token's subject is not an account id.");
        }
        return new JwtAuthenticationToken(jwt, roles.convert(jwt), jwt.getSubject());
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        }
        catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
