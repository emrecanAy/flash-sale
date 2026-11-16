package com.emrecanay.flashsale.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant PLACED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);

    @Test
    void totalIsTheSumOfLineSubtotalsAtReservedPrices() {
        Order order = Order.place(UUID.randomUUID(), "key", List.of(
                new OrderLine(1, 2, new BigDecimal("19.99")),
                new OrderLine(2, 1, new BigDecimal("5.00"))), PLACED_AT, TTL);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("44.98");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getExpiresAt()).isEqualTo(PLACED_AT.plus(TTL));
    }

    @Test
    void canBePaidWhileTheReservationIsValid() {
        Order order = pendingOrder();

        order.pay(PLACED_AT.plus(TTL).minusSeconds(1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void cannotBePaidOnceTheReservationHasRunOutEvenIfTheSweeperHasNotRunYet() {
        Order order = pendingOrder();

        assertThatThrownBy(() -> order.pay(PLACED_AT.plus(TTL)))
                .isInstanceOf(ReservationExpiredException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void aSettledOrderRejectsFurtherTransitions() {
        Order order = pendingOrder();
        order.pay(PLACED_AT);

        assertThatThrownBy(order::cancel).isInstanceOf(OrderNotPendingException.class);
        assertThatThrownBy(order::expire).isInstanceOf(OrderNotPendingException.class);
        assertThatThrownBy(() -> order.pay(PLACED_AT)).isInstanceOf(OrderNotPendingException.class);
    }

    private static Order pendingOrder() {
        return Order.place(UUID.randomUUID(), "key", List.of(new OrderLine(1, 1, BigDecimal.TEN)), PLACED_AT, TTL);
    }
}
