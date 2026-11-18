package com.emrecanay.flashsale.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Injects the signed-in customer's id, taken from the access token's {@code sub} claim. The id is
 * never read from the request body, so one customer cannot act under another's id.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(hidden = true)
@AuthenticationPrincipal(expression = "T(java.util.UUID).fromString(subject)", errorOnInvalidType = true)
public @interface CurrentCustomer {
}
