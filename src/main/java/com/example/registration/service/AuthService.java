package com.example.registration.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.registration.dto.LoginRequest;
import com.example.registration.dto.LoginResponse;
import com.example.registration.entity.JwtToken;
import com.example.registration.entity.User;
import com.example.registration.exception.BadRequestException;
import com.example.registration.exception.UnauthorizedException;
import com.example.registration.repository.JwtTokenRepository;
import com.example.registration.repository.UserRepository;
import com.example.registration.security.JwtService;

import io.jsonwebtoken.JwtException;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^\\S+@\\S+\\.\\S+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");

    private final UserRepository userRepository;
    private final JwtTokenRepository jwtTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, JwtTokenRepository jwtTokenRepository,
            BCryptPasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtTokenRepository = jwtTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();

        User user;
        if (identifier.contains("@")) {
            String email = identifier.toLowerCase();
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new BadRequestException("identifier must be a valid email address");
            }
            user = userRepository.findByEmail(email).orElse(null);
        } else {
            String phone = identifier;
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw new BadRequestException("identifier must be a 10-digit phone number");
            }
            user = userRepository.findByPhone(phone).orElse(null);
        }

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Incorrect credentials");
        }

        Instant expiresAt = Instant.now().plusMillis(jwtService.getExpirationMs());
        LocalDateTime expiresAtLocal = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);

        String token = jwtService.generateToken(user);

        JwtToken jwtToken = new JwtToken();
        jwtToken.setUser(user);
        jwtToken.setToken(token);
        jwtToken.setExpiresAt(expiresAtLocal);
        jwtTokenRepository.save(jwtToken);

        return new LoginResponse(token, "Bearer", expiresAtLocal, user.getId(),
                user.getFullName(), user.getEmail(), user.getRole());
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        try {
            jwtService.parseToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired token");
        }
        jwtTokenRepository.deleteByToken(token);
    }
}
