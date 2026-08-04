package com.example.registration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminRecentOrderResponse(
        String orderId,
        String customerName,
        BigDecimal totalAmount,
        String status,
        LocalDateTime createdAt
) {
}
