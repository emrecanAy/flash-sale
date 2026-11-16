package com.emrecanay.flashsale.order;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final OrderPlacement placement;
    private final OrderLifecycle lifecycle;

    OrderController(OrderPlacement placement, OrderLifecycle lifecycle) {
        this.placement = placement;
        this.lifecycle = lifecycle;
    }

    /** 201 for a new order, 200 when the same request was already processed and is being replayed. */
    @PostMapping
    ResponseEntity<OrderResponse> place(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderPlacement.Result result = placement.place(request.customerId(), idempotencyKey,
                request.quantitiesByProductId());
        if (result.replayed()) {
            return ResponseEntity.ok(result.order());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(result.order().id())
                .toUri();
        return ResponseEntity.created(location).body(result.order());
    }

    @GetMapping("/{orderId}")
    OrderResponse get(@PathVariable UUID orderId) {
        return lifecycle.get(orderId);
    }

    @PostMapping("/{orderId}/payment")
    OrderResponse pay(@PathVariable UUID orderId) {
        return lifecycle.pay(orderId);
    }

    @PostMapping("/{orderId}/cancellation")
    OrderResponse cancel(@PathVariable UUID orderId) {
        return lifecycle.cancel(orderId);
    }
}
