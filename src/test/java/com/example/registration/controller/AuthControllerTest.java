package com.example.registration.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.registration.config.WebConfig;
import com.example.registration.dto.LoginRequest;
import com.example.registration.dto.LoginResponse;
import com.example.registration.entity.Role;
import com.example.registration.exception.BadRequestException;
import com.example.registration.exception.UnauthorizedException;
import com.example.registration.service.AuthService;

@WebMvcTest(AuthController.class)
@Import(WebConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    private LoginResponse loginResponse() {
        return new LoginResponse("jwt-token", "Bearer",
                LocalDateTime.now().plusHours(24), 1, "Asha Rao", "asha@example.com", Role.CUSTOMER);
    }

    @Test
    void login_byEmail_returns200WithToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "Asha@Example.com",
                                  "password": "StrongPass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void login_byPhone_returns200WithToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "8000000001",
                                  "password": "StrongPass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void login_emailWrongPassword_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Incorrect credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "asha@example.com",
                                  "password": "WrongPass!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect credentials"));
    }

    @Test
    void login_phoneWrongPassword_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Incorrect credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "8000000001",
                                  "password": "WrongPass!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect credentials"));
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Incorrect credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "nobody@example.com",
                                  "password": "StrongPass123!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect credentials"));
    }

    @Test
    void login_unknownPhone_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Incorrect credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "9999999999",
                                  "password": "StrongPass123!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect credentials"));
    }

    @Test
    void login_invalidPhoneLength_returns400() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadRequestException("identifier must be a 10-digit phone number"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "12345",
                                  "password": "StrongPass123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("identifier must be a 10-digit phone number"));
    }

    @Test
    void login_invalidEmail_returns400() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadRequestException("identifier must be a valid email address"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "invalid@email",
                                  "password": "StrongPass123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("identifier must be a valid email address"));
    }

    @Test
    void login_emptyPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "asha@example.com",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_withBearerToken_returns200AndDeletesToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        verify(authService).logout("jwt-token");
    }

    @Test
    void logout_withoutBody_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));
    }

    @Test
    void logout_withoutToken_returns401() throws Exception {
        doThrow(new UnauthorizedException("Authentication required"))
                .when(authService).logout(null);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}
