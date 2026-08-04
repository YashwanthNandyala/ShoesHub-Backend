package com.example.registration.dto;

import java.math.BigDecimal;

public record RevenuePoint(
        String label,
        BigDecimal amount
) {
}
