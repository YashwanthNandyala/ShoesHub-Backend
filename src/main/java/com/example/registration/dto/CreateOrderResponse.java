package com.example.registration.dto;

public record CreateOrderResponse(
        String razorpayOrderId,
        String applicationOrderId,
        Integer amount,
        String currency,
        String keyId
) {
}
