package com.example.registration.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPaymentRequest(
        @NotBlank(message = "razorpayPaymentId is required")
        String razorpayPaymentId,

        @NotBlank(message = "razorpayOrderId is required")
        String razorpayOrderId,

        @NotBlank(message = "razorpaySignature is required")
        String razorpaySignature
) {
}
