package com.example.registration.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.registration.dto.RegisterRequest;
import com.example.registration.dto.RegisterResponse;
import com.example.registration.entity.Role;
import com.example.registration.entity.User;
import com.example.registration.exception.DuplicateResourceException;
import com.example.registration.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String userName = request.fullName().trim();
        String phone = request.phone().trim();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (userRepository.existsByFullName(userName)) {
            throw new DuplicateResourceException("User name is already registered");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone number is already registered");
        }

        User user = new User();
        user.setFullName(userName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(Role.CUSTOMER);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);

        return new RegisterResponse(saved.getId(), saved.getFullName(), saved.getEmail(),
                saved.getRole(), saved.getCreatedAt());
    }
}
