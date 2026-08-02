package com.example.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.registration.dto.LoginRequest;
import com.example.registration.dto.LoginResponse;
import com.example.registration.entity.JwtToken;
import com.example.registration.entity.Role;
import com.example.registration.entity.User;
import com.example.registration.exception.BadRequestException;
import com.example.registration.exception.UnauthorizedException;
import com.example.registration.repository.JwtTokenRepository;
import com.example.registration.repository.UserRepository;
import com.example.registration.security.JwtService;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenRepository jwtTokenRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final JwtService jwtService =
            new JwtService("test-secret-key-that-is-long-enough-for-hs256-0123456789", 86400000);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, jwtTokenRepository, encoder, jwtService);
    }

    private User userWithCredentials(String email, String phone, String rawPassword) {
        User user = new User();
        user.setId(10);
        user.setFullName("Asha Rao");
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(Role.CUSTOMER);
        user.setPasswordHash(encoder.encode(rawPassword));
        return user;
    }

    @Test
    void login_byEmail_returnsTokenAndPersistsJwt() {
        User user = userWithCredentials("asha@example.com", "8000000001", "Password@123");
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(new LoginRequest("Asha@Example.com", "Password@123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());
        assertThat(response.email()).isEqualTo("asha@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);

        ArgumentCaptor<JwtToken> captor = ArgumentCaptor.forClass(JwtToken.class);
        verify(jwtTokenRepository).save(captor.capture());
        JwtToken saved = captor.getValue();
        assertThat(saved.getToken()).isEqualTo(response.token());
        assertThat(saved.getUser().getId()).isEqualTo(10);

        Claims claims = jwtService.parseToken(response.token());
        assertThat(claims.getSubject()).isEqualTo("asha@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void login_byPhone_returnsTokenAndPersistsJwt() {
        User user = userWithCredentials("asha@example.com", "8000000001", "Password@123");
        when(userRepository.findByPhone("8000000001")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(new LoginRequest("8000000001", "Password@123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.email()).isEqualTo("asha@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);

        Claims claims = jwtService.parseToken(response.token());
        assertThat(claims.getSubject()).isEqualTo("asha@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void login_wrongPassword_email_throwsUnauthorized() {
        User user = userWithCredentials("asha@example.com", "8000000001", "Password@123");
        when(userRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "WrongPass!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Incorrect credentials");
    }

    @Test
    void login_wrongPassword_phone_throwsUnauthorized() {
        User user = userWithCredentials("asha@example.com", "8000000001", "Password@123");
        when(userRepository.findByPhone("8000000001")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("8000000001", "WrongPass!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Incorrect credentials");
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("nope@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nope@example.com", "Password@123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Incorrect credentials");
    }

    @Test
    void login_unknownPhone_throwsUnauthorized() {
        when(userRepository.findByPhone("9999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("9999999999", "Password@123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Incorrect credentials");
    }

    @Test
    void login_invalidPhoneLength_throwsBadRequest() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("12345", "Password@123")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void login_invalidEmailFormat_throwsBadRequest() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("invalid@email", "Password@123")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void loginResponse_neverContainsPassword() {
        assertThat(LoginResponse.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("password") || field.getName().equals("passwordHash"));
    }
}
