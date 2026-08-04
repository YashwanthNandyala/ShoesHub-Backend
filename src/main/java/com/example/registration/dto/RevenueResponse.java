package com.example.registration.dto;

import java.math.BigDecimal;
import java.util.List;

public record RevenueResponse(
        List<RevenuePoint> points,
        BigDecimal total
) {
}
