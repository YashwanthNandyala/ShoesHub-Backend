package com.example.registration.dto;

import java.time.LocalDateTime;

import com.example.registration.entity.Role;

public record RegisterResponse(
        Integer id,
        String fullName,
        String email,
        Role role,
        LocalDateTime createdAt
) {
}
