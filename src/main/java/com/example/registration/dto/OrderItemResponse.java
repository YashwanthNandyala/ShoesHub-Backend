package com.example.registration.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Integer productId,
        String name,
        String description,
        String imageUrl,
        String category,
        Integer quantity,
        BigDecimal pricePerUnit,
        BigDecimal totalPrice
) {
}
