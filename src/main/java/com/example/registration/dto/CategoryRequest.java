package com.example.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "category name is required")
        @Size(max = 100, message = "category name must be at most 100 characters")
        String categoryName
) {
}
