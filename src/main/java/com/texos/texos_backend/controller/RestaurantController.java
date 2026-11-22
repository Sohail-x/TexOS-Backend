package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.RestaurantRegistrationRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.RestaurantService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final OwnerRepository ownerRepository;

    @PostMapping("/owner/{ownerId}")
    public ResponseEntity<?> createRestaurant(
            @PathVariable Long ownerId,
            @Valid @RequestBody RestaurantRegistrationRequest request,
            Authentication authentication) {

        Owner authenticatedOwner = getAuthenticatedOwner(authentication);
        if (authenticatedOwner == null || !authenticatedOwner.getId().equals(ownerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot create a restaurant for another owner"));
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName(request.getRestaurantName());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone());
        restaurant.setEmail(request.getEmail());
        restaurant.setGoogleMapsUrl(request.getGoogleMapsUrl());

        Restaurant saved = restaurantService.createRestaurant(ownerId, restaurant);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<?> getRestaurantsByOwner(
            @PathVariable Long ownerId,
            Authentication authentication) {

        Owner authenticatedOwner = getAuthenticatedOwner(authentication);
        if (authenticatedOwner == null || !authenticatedOwner.getId().equals(ownerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot access another owner's restaurants"));
        }

        List<Map<String, Object>> restaurants = restaurantService
                .getRestaurantsByOwnerId(ownerId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(restaurants);
    }

    @GetMapping("/{restaurantId}/owner/{ownerId}")
    public ResponseEntity<?> getRestaurantById(
            @PathVariable Long restaurantId,
            @PathVariable Long ownerId,
            Authentication authentication) {

        Owner authenticatedOwner = getAuthenticatedOwner(authentication);
        if (authenticatedOwner == null || !authenticatedOwner.getId().equals(ownerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot access another owner's restaurant"));
        }

        Restaurant restaurant = restaurantService.getRestaurantById(restaurantId, ownerId);
        return ResponseEntity.ok(toResponse(restaurant));
    }

    @PutMapping("/{restaurantId}")
    public ResponseEntity<?> updateRestaurant(
            @PathVariable Long restaurantId,
            @Valid @RequestBody RestaurantRegistrationRequest request,
            Authentication authentication) {
        Owner authenticatedOwner = getAuthenticatedOwner(authentication);
        if (authenticatedOwner == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot update this restaurant"));
        }

        Restaurant restaurant = restaurantService.updateRestaurant(
                restaurantId, authenticatedOwner.getId(), request);
        return ResponseEntity.ok(toResponse(restaurant));
    }

    @DeleteMapping("/{restaurantId}")
    public ResponseEntity<?> removeRestaurant(
            @PathVariable Long restaurantId,
            Authentication authentication) {
        Owner authenticatedOwner = getAuthenticatedOwner(authentication);
        if (authenticatedOwner == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot remove this restaurant"));
        }

        restaurantService.removeRestaurant(restaurantId, authenticatedOwner.getId());
        return ResponseEntity.noContent().build();
    }

    private Owner getAuthenticatedOwner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return ownerRepository.findByEmail(authentication.getName())
                .or(() -> ownerRepository.findByPhone(authentication.getName())).orElse(null);
    }

    private Map<String, Object> toResponse(Restaurant restaurant) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("restaurantId", restaurant.getId());
        response.put("restaurantName", restaurant.getRestaurantName());
        response.put("address", restaurant.getAddress());
        response.put("phone", restaurant.getPhone());
        response.put("email", restaurant.getEmail());
        response.put("googleMapsUrl", restaurant.getGoogleMapsUrl());
        response.put("openingHours", restaurant.getOpeningHours());
        response.put("active", restaurant.isActive());
        response.put("ownerId", restaurant.getOwner().getId());
        return response;
    }
}
