package com.example.registration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        String orderId,
        String status,
        LocalDateTime orderDate,
        int itemCount,
        BigDecimal grandTotal,
        List<OrderItemResponse> items
) {
}
