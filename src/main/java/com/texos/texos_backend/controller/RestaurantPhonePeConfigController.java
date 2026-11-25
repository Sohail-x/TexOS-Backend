package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.RestaurantPhonePeConfigRequest;
import com.texos.texos_backend.config.AuthenticatedOwnerPrincipal;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.RestaurantPhonePeConfigService;
import jakarta.servlet.http.HttpServletRequest;
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
            @PathVariable Long restaurantId,
            Authentication authentication,
            HttpServletRequest request) {
        Long ownerId = requireOwnerId(authentication, request);
        return ResponseEntity.ok(configService.getSettings(restaurantId, ownerId));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> saveSettings(
            @PathVariable Long restaurantId,
            @Valid @RequestBody RestaurantPhonePeConfigRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        Long ownerId = requireOwnerId(authentication, servletRequest);
        return ResponseEntity.ok(configService.saveSettings(restaurantId, ownerId, request));
    }

    private Long requireOwnerId(
            Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Authentication required");
        }
        Object principalValue =
                request.getAttribute(AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE);
        if (principalValue instanceof AuthenticatedOwnerPrincipal principal) {
            return principal.ownerId();
        }
        Owner owner = ownerRepository.findByEmail(authentication.getName())
                .or(() -> ownerRepository.findByPhone(authentication.getName()))
                .orElseThrow(() -> new SecurityException("Owner account is unavailable"));
        return owner.getId();
    }
}
