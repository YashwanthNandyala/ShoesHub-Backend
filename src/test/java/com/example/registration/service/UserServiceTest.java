package com.example.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.registration.dto.RegisterRequest;
import com.example.registration.dto.RegisterResponse;
import com.example.registration.entity.Role;
import com.example.registration.entity.User;
import com.example.registration.exception.DuplicateResourceException;
import com.example.registration.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Spy
    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @InjectMocks
    private UserService userService;

    @Test
    void register_lowercasesEmail_andStoresBcryptHash() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByFullName(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1);
            return user;
        });

        RegisterRequest request = new RegisterRequest("Asha Rao", "Asha@Example.com", "StrongPass123!");

        RegisterResponse response = userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getPasswordHash()).isNotEqualTo("StrongPass123!");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(encoder.matches("StrongPass123!", saved.getPasswordHash())).isTrue();

        assertThat(response.email()).isEqualTo("asha@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.id()).isEqualTo(1);
    }

    @Test
    void register_throwsWhenEmailIsDuplicate() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("Asha Rao", "Asha@Example.com", "StrongPass123!");

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email is already registered");
    }

    @Test
    void register_throwsWhenUserNameIsDuplicate() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(userRepository.existsByFullName("Asha Rao")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("Asha Rao", "Asha@Example.com", "StrongPass123!");

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("User name is already registered");
    }

    @Test
    void response_neverContainsPassword() {
        assertThat(RegisterResponse.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("password") || field.getName().equals("passwordHash"));
    }
}
