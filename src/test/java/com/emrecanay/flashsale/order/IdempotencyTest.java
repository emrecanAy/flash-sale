package com.emrecanay.flashsale.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class IdempotencyTest {

    @Autowired
    OrderPlacement placement;

    @Autowired
    TestProducts products;

    @Test
    void retryingWithTheSameKeyReturnsTheOriginalOrderAndReservesOnce() {
        long productId = products.createWithStock(5);
        UUID customerId = UUID.randomUUID();

        OrderPlacement.Result first = placement.place(customerId, "checkout-1", Map.of(productId, 2));
        OrderPlacement.Result retry = placement.place(customerId, "checkout-1", Map.of(productId, 2));

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.order().id()).isEqualTo(first.order().id());
        assertThat(products.stockOf(productId)).isEqualTo(3);
    }

    /**
     * The hardest case: a double-submitted checkout for the last unit. Whichever request loses
     * the race must come back with the winner's order, not with "out of stock".
     */
    @Test
    void concurrentDuplicatesForTheLastUnitAllReceiveTheSameOrder() throws Exception {
        long productId = products.createWithStock(1);
        UUID customerId = UUID.randomUUID();
        Callable<UUID> submit = () -> placement.place(customerId, "double-click", Map.of(productId, 1)).order().id();

        List<UUID> orderIds;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<UUID>> futures = executor.invokeAll(IntStream.range(0, 20).mapToObj(i -> submit).toList());
            orderIds = futures.stream().map(Future::resultNow).toList();
        }

        assertThat(orderIds).hasSize(20);
        assertThat(orderIds).containsOnly(orderIds.getFirst());
        assertThat(products.stockOf(productId)).isZero();
        assertThat(products.unitsHeldByOrders(productId)).isEqualTo(1);
    }

    @Test
    void theSameKeyFromAnotherCustomerIsAnIndependentOrder() {
        long productId = products.createWithStock(2);

        OrderPlacement.Result first = placement.place(UUID.randomUUID(), "shared-key", Map.of(productId, 1));
        OrderPlacement.Result second = placement.place(UUID.randomUUID(), "shared-key", Map.of(productId, 1));

        assertThat(second.replayed()).isFalse();
        assertThat(second.order().id()).isNotEqualTo(first.order().id());
    }

    @Test
    void reusingAKeyForDifferentItemsIsRejectedInsteadOfSilentlyReplayed() {
        long productId = products.createWithStock(5);
        UUID customerId = UUID.randomUUID();
        placement.place(customerId, "checkout-2", Map.of(productId, 1));

        assertThatThrownBy(() -> placement.place(customerId, "checkout-2", Map.of(productId, 3)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(products.stockOf(productId)).isEqualTo(4);
    }
}
