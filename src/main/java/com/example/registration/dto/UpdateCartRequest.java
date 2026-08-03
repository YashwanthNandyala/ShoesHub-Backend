package com.example.registration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record UpdateCartRequest(
        @NotNull @Positive Integer productId,
        @NotNull @Pattern(regexp = "INCREMENT|DECREMENT") String action
) {
}
