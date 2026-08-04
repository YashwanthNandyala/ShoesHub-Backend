package com.example.registration.dto;

import com.example.registration.entity.Role;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "role is required")
        Role role
) {
}
