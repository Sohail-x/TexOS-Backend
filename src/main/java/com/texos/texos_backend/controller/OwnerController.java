package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.AccountDeletionRequest;
import com.texos.texos_backend.config.AuthenticatedOwnerPrincipal;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.dto.OwnerRegistrationRequest;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.service.OwnerService;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.service.RestaurantService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;
    private final RestaurantService restaurantService;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> registerOwner(
            @Valid @RequestBody OwnerRegistrationRequest request) {

        Owner owner = new Owner();

        owner.setFullName(request.getFullName());
        owner.setEmail(request.getEmail() == null || request.getEmail().isBlank()
                ? null : request.getEmail());
        owner.setPasswordHash(request.getPassword());
        owner.setPhone(request.getPhone());
        owner.setAuthProvider("LOCAL");

        Owner savedOwner = ownerService.registerOwner(owner);
        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName(request.getRestaurantName().trim());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone().trim());
        restaurant.setEmail(request.getEmail() == null || request.getEmail().isBlank()
                ? null : request.getEmail().trim().toLowerCase());
        Restaurant savedRestaurant = restaurantService.createRestaurant(savedOwner.getId(), restaurant);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Owner registered successfully");
        response.put("ownerId", savedOwner.getId());
        response.put("fullName", savedOwner.getFullName());
        response.put("email", savedOwner.getEmail());
        response.put("phone", savedOwner.getPhone());
        response.put("restaurantId", savedRestaurant.getId());
        response.put("restaurantName", savedRestaurant.getRestaurantName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteMyAccount(
            @Valid @RequestBody AccountDeletionRequest request,
            HttpServletRequest httpRequest) {
        if (!"DELETE".equals(request.confirmation())) {
            throw new InvalidRequestException("Type DELETE to confirm account deletion.");
        }

        Object principal = httpRequest.getAttribute(
                AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE);
        if (!(principal instanceof AuthenticatedOwnerPrincipal ownerPrincipal)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Owner authentication is required.");
        }

        ownerService.deleteOwnerAccount(ownerPrincipal.ownerId());
        return ResponseEntity.ok(Map.of(
                "message", "Owner account deactivated. Profile anonymization is scheduled within 30 days."
        ));
    }
}