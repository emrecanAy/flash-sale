package com.emrecanay.flashsale.order;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("flashsale.reservation")
record ReservationProperties(@NotNull Duration ttl, @Positive int sweepBatchSize) {
}
