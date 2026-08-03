package com.example.registration.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Integer productId,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        Integer categoryId,
        String imageUrl
) {
}
