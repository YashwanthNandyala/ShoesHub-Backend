package com.example.registration.dto;

import java.time.LocalDateTime;

import com.example.registration.entity.Role;

public record LoginResponse(
        String token,
        String tokenType,
        LocalDateTime expiresAt,
        Integer id,
        String fullName,
        String email,
        Role role
) {
}
