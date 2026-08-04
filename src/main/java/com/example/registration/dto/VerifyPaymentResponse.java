package com.example.registration.dto;

public record VerifyPaymentResponse(
        boolean success,
        String message,
        String orderId,
        String paymentId,
        String paymentStatus,
        String orderStatus
) {
}
