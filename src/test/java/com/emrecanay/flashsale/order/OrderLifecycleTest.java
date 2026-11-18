package com.emrecanay.flashsale.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.emrecanay.flashsale.support.IntegrationTest;
import com.emrecanay.flashsale.support.TestProducts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class OrderLifecycleTest {

    @Autowired
    OrderPlacement placement;

    @Autowired
    OrderLifecycle lifecycle;

    @Autowired
    ReservationProperties reservation;

    @Autowired
    TestProducts products;

    /** JUnit makes a new instance per test, so every test has its own customer. */
    final UUID customer = UUID.randomUUID();

    @Test
    void cancellingAPendingOrderPutsItsStockBackOnSale() {
        long productId = products.createWithStock(3);
        UUID orderId = placeOrder(productId, 2);

        OrderResponse cancelled = lifecycle.cancel(orderId, customer);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(products.stockOf(productId)).isEqualTo(3);
    }

    /** Without the row lock every concurrent cancel would see PENDING and release the stock again. */
    @Test
    void concurrentCancelsReleaseTheStockExactlyOnce() throws Exception {
        long productId = products.createWithStock(3);
        UUID orderId = placeOrder(productId, 2);
        Callable<Boolean> cancel = () -> {
            try {
                lifecycle.cancel(orderId, customer);
                return true;
            }
            catch (OrderNotPendingException alreadyCancelled) {
                return false;
            }
        };

        List<Boolean> outcomes;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = executor.invokeAll(IntStream.range(0, 10).mapToObj(i -> cancel).toList());
            outcomes = futures.stream().map(Future::resultNow).toList();
        }

        assertThat(outcomes).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(products.stockOf(productId)).isEqualTo(3);
    }

    @Test
    void aPaidOrderKeepsItsStockAndCanNoLongerBeCancelled() {
        long productId = products.createWithStock(3);
        UUID orderId = placeOrder(productId, 1);

        lifecycle.pay(orderId, customer);

        assertThatThrownBy(() -> lifecycle.cancel(orderId, customer)).isInstanceOf(OrderNotPendingException.class);
        assertThat(lifecycle.get(orderId, customer).status()).isEqualTo(OrderStatus.PAID);
        assertThat(products.stockOf(productId)).isEqualTo(2);
    }

    @Test
    void unpaidOrdersExpireAndReleaseTheirStockWhilePaidOnesAreLeftAlone() {
        long productId = products.createWithStock(5);
        UUID unpaid = placeOrder(productId, 2);
        UUID paid = placeOrder(productId, 1);
        lifecycle.pay(paid, customer);

        sweepAsOf(afterReservationWindow());

        assertThat(lifecycle.get(unpaid, customer).status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(lifecycle.get(paid, customer).status()).isEqualTo(OrderStatus.PAID);
        assertThat(products.stockOf(productId)).isEqualTo(4);
    }

    @Test
    void ordersInsideTheirReservationWindowAreNotExpired() {
        long productId = products.createWithStock(1);
        UUID orderId = placeOrder(productId, 1);

        sweepAsOf(Instant.now());

        assertThat(lifecycle.get(orderId, customer).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(products.stockOf(productId)).isZero();
    }

    @Test
    void expiredOrdersCannotBePaid() {
        long productId = products.createWithStock(1);
        UUID orderId = placeOrder(productId, 1);
        sweepAsOf(afterReservationWindow());

        assertThatThrownBy(() -> lifecycle.pay(orderId, customer)).isInstanceOf(OrderNotPendingException.class);
    }

    /** The lookup is by order id and owner together, so a stranger cannot lock the row, let alone change it. */
    @Test
    void anotherCustomerCannotSeePayOrCancelTheOrder() {
        long productId = products.createWithStock(3);
        UUID orderId = placeOrder(productId, 2);
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> lifecycle.get(orderId, stranger)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> lifecycle.pay(orderId, stranger)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> lifecycle.cancel(orderId, stranger)).isInstanceOf(OrderNotFoundException.class);

        assertThat(lifecycle.get(orderId, customer).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(products.stockOf(productId)).isEqualTo(1);
    }

    private UUID placeOrder(long productId, int quantity) {
        return placement.place(customer, UUID.randomUUID().toString(), Map.of(productId, quantity))
                .order().id();
    }

    private Instant afterReservationWindow() {
        return Instant.now().plus(reservation.ttl()).plus(Duration.ofMinutes(1));
    }

    private void sweepAsOf(Instant now) {
        while (lifecycle.expireOverdue(now) > 0) {
            // drain every batch
        }
    }
}
