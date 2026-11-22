package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.RestaurantPhonePeConfigRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.RestaurantPhonePeConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/phonepe-settings")
@RequiredArgsConstructor
public class RestaurantPhonePeConfigController {
    private final RestaurantPhonePeConfigService configService;
    private final OwnerRepository ownerRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings(
            @PathVariable Long restaurantId, Authentication authentication) {
        Owner owner = requireOwner(authentication);
        return ResponseEntity.ok(configService.getSettings(restaurantId, owner.getId()));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> saveSettings(
            @PathVariable Long restaurantId,
            @Valid @RequestBody RestaurantPhonePeConfigRequest request,
            Authentication authentication) {
        Owner owner = requireOwner(authentication);
        return ResponseEntity.ok(configService.saveSettings(restaurantId, owner.getId(), request));
    }

    private Owner requireOwner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Authentication required");
        }
        return ownerRepository.findByEmail(authentication.getName())
                .or(() -> ownerRepository.findByPhone(authentication.getName()))
                .orElseThrow(() -> new SecurityException("Owner account is unavailable"));
    }
}
