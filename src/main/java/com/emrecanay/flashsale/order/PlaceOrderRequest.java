package com.emrecanay.flashsale.order;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record PlaceOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty @Size(max = 20) List<@Valid Item> items) {

    record Item(@NotNull Long productId, @Min(1) @Max(100) int quantity) {
    }

    /** The same product listed twice is one line with the summed quantity. */
    Map<Long, Integer> quantitiesByProductId() {
        return items.stream().collect(Collectors.toMap(Item::productId, Item::quantity, Integer::sum));
    }
}
