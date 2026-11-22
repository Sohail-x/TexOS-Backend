package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.AdminLoginRequest;
import com.texos.texos_backend.service.AdminCredentials;
import com.texos.texos_backend.service.JwtService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminCredentials adminCredentials;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthController(
            AdminCredentials adminCredentials,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.adminCredentials = adminCredentials;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AdminLoginRequest request) {
        if (!adminCredentials.matches(request.getUsername(), request.getPassword(), passwordEncoder)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid admin username or password"));
        }

        return ResponseEntity.ok(Map.of(
                "token", jwtService.generateAdminToken(request.getUsername()),
                "tokenType", "Bearer",
                "username", request.getUsername()));
    }
}
