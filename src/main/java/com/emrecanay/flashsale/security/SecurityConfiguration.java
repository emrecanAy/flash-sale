package com.emrecanay.flashsale.security;

import static com.emrecanay.flashsale.security.Roles.CATALOG_MANAGER;
import static com.emrecanay.flashsale.security.Roles.CUSTOMER;
import static com.emrecanay.flashsale.security.Roles.OPERATOR;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The service is an OAuth 2.0 resource server. It does not handle passwords or sessions: Keycloak
 * signs short-lived access tokens, and every request carries one as a bearer token. The service
 * checks the signature against the realm's published keys, the issuer, the expiry and that the
 * token was issued for this API ({@code aud}).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver problems,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) throws Exception {
        AuthenticationEntryPoint unauthenticated = withProblemBody(new BearerTokenAuthenticationEntryPoint(), problems);
        AccessDeniedHandler forbidden = withProblemBody(new BearerTokenAccessDeniedHandler(), problems);

        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/products", "/api/v1/products/*/restock")
                            .hasRole(CATALOG_MANAGER)
                        .requestMatchers("/api/v1/orders", "/api/v1/orders/**").hasRole(CUSTOMER)
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole(OPERATOR)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                            .permitAll()
                        // A new endpoint is closed until a rule above opens it.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new CustomerTokenConverter()))
                        // RFC 9728 metadata at /.well-known/oauth-protected-resource, linked from every
                        // 401 challenge, so a client can discover where to get a token.
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                resource -> resource
                                        .resourceName("Flash Sale API")
                                        .authorizationServer(issuer)
                                        .tlsClientCertificateBoundAccessTokens(false)))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                // Bearer tokens are sent explicitly, never by the browser on its own, so there is
                // nothing for a cross-site request to ride on and no server-side session to keep.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Spring's bearer token handlers set the status and the {@code WWW-Authenticate} header that
     * OAuth clients read. The body then comes from {@code ApiExceptionHandler}, so 401 and 403 are
     * problem documents with a {@code code} like every other error.
     */
    private static AuthenticationEntryPoint withProblemBody(BearerTokenAuthenticationEntryPoint headers,
            HandlerExceptionResolver problems) {
        return (request, response, exception) -> {
            headers.commence(request, response, exception);
            problems.resolveException(request, response, null, exception);
        };
    }

    private static AccessDeniedHandler withProblemBody(BearerTokenAccessDeniedHandler headers,
            HandlerExceptionResolver problems) {
        return (request, response, exception) -> {
            headers.handle(request, response, exception);
            problems.resolveException(request, response, null, exception);
        };
    }
}
