package com.texos.texos_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminCredentials {

    private final String username;
    private final String passwordHash;

    public AdminCredentials(
            @Value("${app.admin.username}") String username,
            @Value("${app.admin.password}") String password,
            PasswordEncoder passwordEncoder) {
        this.username = username;
        this.passwordHash = passwordEncoder.encode(password);
    }

    public boolean matches(String candidateUsername, String candidatePassword, PasswordEncoder encoder) {
        return username.equals(candidateUsername)
                && encoder.matches(candidatePassword, passwordHash);
    }

    public boolean isUsername(String candidateUsername) {
        return username.equals(candidateUsername);
    }
}
