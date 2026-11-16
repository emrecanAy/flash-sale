package com.emrecanay.flashsale.catalog;

import jakarta.validation.constraints.Positive;

record RestockRequest(@Positive int quantity) {
}
