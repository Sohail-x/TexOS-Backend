package com.texos.texos_backend.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import com.texos.texos_backend.dto.LoginRequest;
import com.texos.texos_backend.dto.ProfileUpdateRequest;
import com.texos.texos_backend.dto.GoogleTokenRequest;
import com.texos.texos_backend.dto.GoogleOnboardingRequest;
import com.texos.texos_backend.dto.ForgotPasswordRequest;
import com.texos.texos_backend.dto.ResetPasswordRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.service.GoogleIdentityService;
import com.texos.texos_backend.service.RestaurantService;
import com.texos.texos_backend.service.PasswordResetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OwnerRepository ownerRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdentityService googleIdentityService;
    private final RestaurantService restaurantService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String identity = request.getEmail().trim();
        Owner owner = identity.contains("@")
                ? ownerRepository.findByEmail(identity.toLowerCase()).orElse(null)
                : ownerRepository.findByPhone(identity).orElse(null);

        if (owner == null || !owner.isActive()
                || !passwordEncoder.matches(request.getPassword(), owner.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password"));
        }

        return ResponseEntity.ok(createAuthResponse(owner, "Login successful"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "If an account exists for that email, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password has been reset. You can now sign in."));
    }

    @PostMapping("/google")
    public ResponseEntity<?> google(@Valid @RequestBody GoogleTokenRequest request) {
        GoogleIdentityService.VerifiedGoogleIdentity identity =
                googleIdentityService.verify(request.getCredential());
        Owner owner = ownerRepository.findByGoogleSub(identity.subject()).orElse(null);
        if (owner == null) {
            if (ownerRepository.existsByEmail(identity.email())) {
                throw new DuplicateResourceException(
                        "This email already has an account. Sign in with your existing method.");
            }
            owner = new Owner();
            owner.setEmail(identity.email());
            owner.setGoogleSub(identity.subject());
            owner.setAuthProvider("GOOGLE");
            owner.setFullName(identity.name());
            owner.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            owner = ownerRepository.save(owner);
        }
        if (!owner.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Owner account is inactive"));
        }
        return ResponseEntity.ok(createAuthResponse(owner, "Google sign-in successful"));
    }

    @PostMapping("/onboarding")
    @Transactional
    public ResponseEntity<?> completeGoogleOnboarding(
            Authentication authentication,
            @Valid @RequestBody GoogleOnboardingRequest request) {
        Owner owner = findAuthenticatedOwner(authentication);
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required"));
        }
        if (!restaurantRepository.findAllByOwnerIdAndActiveTrue(owner.getId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Restaurant onboarding is already complete"));
        }
        if (ownerRepository.existsByPhoneAndIdNot(request.getPhone().trim(), owner.getId())) {
            throw new DuplicateResourceException("Mobile number is already registered");
        }
        owner.setPhone(request.getPhone().trim());
        ownerRepository.save(owner);
        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName(request.getRestaurantName().trim());
        restaurant.setPhone(request.getPhone().trim());
        restaurant.setAddress(request.getAddress());
        restaurant.setEmail(owner.getEmail());
        Restaurant saved = restaurantService.createRestaurant(owner.getId(), restaurant);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "restaurantId", saved.getId(),
                "restaurantName", saved.getRestaurantName(),
                "phone", saved.getPhone()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        long controllerStartedAtNanos = DashboardRequestMeasurements.startControllerTiming();
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Not authenticated"));
            }

            Owner owner = findAuthenticatedOwner(authentication);
            if (owner == null || !owner.isActive()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Owner not found or inactive"));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ownerId", owner.getId());
            result.put("fullName", owner.getFullName());
            result.put("email", owner.getEmail());
            result.put("phone", owner.getPhone());
            result.put("avatarIcon", owner.getAvatarIcon());
            result.put("avatarImage", owner.getAvatarImage());
            result.put("restaurants", getOwnerRestaurants(owner.getId()));
            return ResponseEntity.ok(result);
        } finally {
            DashboardRequestMeasurements.finishControllerTiming(controllerStartedAtNanos);
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request) {
        Owner owner = findAuthenticatedOwner(authentication);
        if (owner == null || !owner.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Owner account is unavailable"));
        }

        String oldTokenIdentity = owner.getEmail() == null || owner.getEmail().isBlank()
                ? owner.getPhone() : null;
        owner.setFullName(request.getFullName().trim());
        if (ownerRepository.existsByPhoneAndIdNot(request.getPhone().trim(), owner.getId())) {
            throw new DuplicateResourceException("Mobile number is already registered");
        }
        owner.setPhone(request.getPhone().trim());
        owner.setAvatarIcon(request.getAvatarIcon() == null || request.getAvatarIcon().isBlank()
                ? null : request.getAvatarIcon().trim());
        String avatarImage = request.getAvatarImage();
        if (avatarImage != null && !avatarImage.isBlank()
                && !avatarImage.matches("^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=\\r\\n]+$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Profile image must be a PNG, JPEG, WebP, or GIF file."));
        }
        owner.setAvatarImage(avatarImage == null || avatarImage.isBlank() ? null : avatarImage);
        ownerRepository.save(owner);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ownerId", owner.getId());
        response.put("fullName", owner.getFullName());
        response.put("email", owner.getEmail());
        response.put("phone", owner.getPhone());
        response.put("avatarIcon", owner.getAvatarIcon());
        response.put("avatarImage", owner.getAvatarImage());
        if (oldTokenIdentity != null && !oldTokenIdentity.equals(owner.getPhone())) {
            response.put("token", jwtService.generateToken(owner.getPhone(), owner.getTokenVersion()));
        }
        return ResponseEntity.ok(response);
    }

    private List<Map<String, Object>> getOwnerRestaurants(Long ownerId) {
        return restaurantRepository.findAllByOwnerIdAndActiveTrue(ownerId).stream().map(restaurant -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("restaurantId", restaurant.getId());
            item.put("restaurantName", restaurant.getRestaurantName());
            item.put("address", restaurant.getAddress());
            item.put("phone", restaurant.getPhone());
            item.put("email", restaurant.getEmail());
            item.put("googleMapsUrl", restaurant.getGoogleMapsUrl());
            item.put("active", restaurant.isActive());
            return item;
        }).toList();
    }

    private Owner findAuthenticatedOwner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return ownerRepository.findByEmail(authentication.getName())
                .or(() -> ownerRepository.findByPhone(authentication.getName())).orElse(null);
    }

    private Map<String, Object> createAuthResponse(Owner owner, String message) {
        String tokenSubject = owner.getEmail() == null || owner.getEmail().isBlank()
                ? owner.getPhone() : owner.getEmail();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        result.put("token", jwtService.generateToken(tokenSubject, owner.getTokenVersion()));
        result.put("tokenType", "Bearer");
        result.put("ownerId", owner.getId());
        result.put("fullName", owner.getFullName());
        result.put("email", owner.getEmail());
        result.put("phone", owner.getPhone());
        result.put("avatarImage", owner.getAvatarImage());
        result.put("restaurants", getOwnerRestaurants(owner.getId()));
        result.put("onboardingRequired", restaurantRepository
                .findAllByOwnerIdAndActiveTrue(owner.getId()).isEmpty());
        return result;
    }
}
