package com.example.registration.dto;

import java.time.LocalDateTime;

import com.example.registration.entity.Role;

public record UserAdminResponse(
        Integer id,
        String fullName,
        String email,
        String phone,
        Role role,
        LocalDateTime createdAt
) {
}
