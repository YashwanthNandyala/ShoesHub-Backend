package com.example.registration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.registration.entity.JwtToken;

public interface JwtTokenRepository extends JpaRepository<JwtToken, Integer> {

    void deleteByExpiresAtBefore(java.time.LocalDateTime now);
}
