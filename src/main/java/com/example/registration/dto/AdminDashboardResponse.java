package com.example.registration.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminDashboardResponse(
        long userCount,
        long productCount,
        long orderCount,
        long paidOrderCount,
        BigDecimal totalRevenue,
        BigDecimal todayRevenue,
        List<AdminRecentOrderResponse> recentOrders
) {
}
