package com.example.registration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.registration.entity.JwtToken;
import com.example.registration.entity.Role;
import com.example.registration.entity.User;
import com.example.registration.repository.JwtTokenRepository;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 86400000);
    private final JwtTokenRepository jwtTokenRepository = mock(JwtTokenRepository.class);

    private JwtAuthenticationFilter filter;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(7);
        user.setEmail("asha@example.com");
        user.setRole(Role.CUSTOMER);
        filter = new JwtAuthenticationFilter(jwtService, jwtTokenRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String tokenForUser() {
        return jwtService.generateToken(user);
    }

    private void doFilter(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private JwtToken storedToken(String token) {
        JwtToken jwtToken = new JwtToken();
        jwtToken.setToken(token);
        return jwtToken;
    }

    @Test
    void storedValidToken_setsAuthentication() throws Exception {
        String token = tokenForUser();
        when(jwtTokenRepository.findByToken(token)).thenReturn(Optional.of(storedToken(token)));

        doFilter("Bearer " + token);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("asha@example.com");
        assertThat(authentication.getAuthorities())
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER"));
    }

    @Test
    void revokedToken_notInDatabase_doesNotAuthenticate() throws Exception {
        String token = tokenForUser();
        when(jwtTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        doFilter("Bearer " + token);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidToken_doesNotAuthenticate() throws Exception {
        doFilter("Bearer not-a-jwt-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingHeader_doesNotAuthenticate() throws Exception {
        doFilter(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
