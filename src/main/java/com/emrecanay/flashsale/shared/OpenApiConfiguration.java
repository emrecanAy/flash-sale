package com.emrecanay.flashsale.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String KEYCLOAK = "keycloak";

    /**
     * Swagger UI signs in through Keycloak with the authorization code flow and PKCE, the flow meant
     * for public clients such as a browser page. It never sees a password.
     */
    @Bean
    OpenAPI flashSaleApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                .tokenUrl(issuer + "/protocol/openid-connect/token")
                .scopes(new Scopes().addString("openid", "Sign in"));
        return new OpenAPI()
                .info(new Info()
                        .title("Flash Sale API")
                        .version("v1")
                        .description("Order service for limited-stock product drops. Browsing is open; "
                                + "ordering needs the customer role, managing products the catalog-manager role."))
                .components(new Components().addSecuritySchemes(KEYCLOAK, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(authorizationCode))))
                .addSecurityItem(new SecurityRequirement().addList(KEYCLOAK));
    }
}
