package com.example.registration.dto;

import java.math.BigDecimal;

public record OverallRevenueResponse(
        BigDecimal totalRevenue,
        long totalOrders,
        long paidOrders,
        BigDecimal averageOrderValue
) {
}
