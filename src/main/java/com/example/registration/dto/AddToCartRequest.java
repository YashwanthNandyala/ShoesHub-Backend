package com.example.registration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddToCartRequest(
        @NotNull @Positive Integer productId
) {
}
