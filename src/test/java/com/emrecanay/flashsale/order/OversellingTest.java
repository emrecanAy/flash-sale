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

import com.emrecanay.flashsale.catalog.InsufficientStockException;
import com.emrecanay.flashsale.support.IntegrationTest;
import com.emrecanay.flashsale.support.TestProducts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class OversellingTest {

    @Autowired
    OrderPlacement placement;

    @Autowired
    TestProducts products;

    @Test
    void twoHundredBuyersCompetingForTenUnitsProduceExactlyTenOrders() throws Exception {
        long productId = products.createWithStock(10);

        List<Boolean> outcomes = runConcurrently(200, () -> {
            try {
                placement.place(UUID.randomUUID(), UUID.randomUUID().toString(), Map.of(productId, 1));
                return true;
            }
            catch (InsufficientStockException soldOut) {
                return false;
            }
        });

        assertThat(outcomes).filteredOn(Boolean::booleanValue).hasSize(10);
        assertThat(products.stockOf(productId)).isZero();
        assertThat(products.unitsHeldByOrders(productId)).isEqualTo(10);
    }

    @Test
    void anOrderThatCannotBeFullyStockedReservesNothing() {
        long plentiful = products.createWithStock(5);
        long scarce = products.createWithStock(1);

        assertThatThrownBy(() -> placement.place(UUID.randomUUID(), "key", Map.of(plentiful, 2, scarce, 2)))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(products.stockOf(plentiful)).isEqualTo(5);
        assertThat(products.stockOf(scarce)).isEqualTo(1);
    }

    /** Would fail with a PostgreSQL deadlock error if product rows were locked in request order. */
    @Test
    void ordersOverlappingOnSeveralProductsDoNotDeadlock() throws Exception {
        long first = products.createWithStock(100);
        long second = products.createWithStock(100);

        List<Boolean> outcomes = runConcurrently(100, () -> {
            placement.place(UUID.randomUUID(), UUID.randomUUID().toString(), Map.of(first, 1, second, 1));
            return true;
        });

        assertThat(outcomes).hasSize(100).containsOnly(true);
        assertThat(products.stockOf(first)).isZero();
        assertThat(products.stockOf(second)).isZero();
    }

    private static <T> List<T> runConcurrently(int times, Callable<T> task) throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = executor.invokeAll(IntStream.range(0, times).mapToObj(i -> task).toList());
            return futures.stream().map(Future::resultNow).toList();
        }
    }
}
