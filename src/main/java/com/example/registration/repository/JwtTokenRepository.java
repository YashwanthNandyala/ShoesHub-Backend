package com.example.registration.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.registration.entity.JwtToken;

public interface JwtTokenRepository extends JpaRepository<JwtToken, Integer> {

    Optional<JwtToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
