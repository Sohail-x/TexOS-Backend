package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.RestaurantBillingSettingsRequest;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/billing-settings")
@RequiredArgsConstructor
public class RestaurantBillingController {

    private final RestaurantService restaurantService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings(
            @PathVariable Long restaurantId
    ) {
        Restaurant restaurant = restaurantService.getBillingSettings(restaurantId);

        return ResponseEntity.ok(Map.of(
                "restaurantId", restaurant.getId(),
                "menuPriceTaxMode", restaurant.getMenuPriceTaxMode(),
                "taxRate", restaurant.getTaxRate(),
                "serviceChargeEnabled", restaurant.isServiceChargeEnabled(),
                "serviceChargeRate", restaurant.getServiceChargeRate()
        ));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateSettings(
            @PathVariable Long restaurantId,
            @Valid @RequestBody RestaurantBillingSettingsRequest request
    ) {
        Restaurant restaurant = restaurantService.updateBillingSettings(restaurantId, request);

        return ResponseEntity.ok(Map.of(
                "message", "Billing settings updated successfully",
                "restaurantId", restaurant.getId(),
                "menuPriceTaxMode", restaurant.getMenuPriceTaxMode(),
                "taxRate", restaurant.getTaxRate(),
                "serviceChargeEnabled", restaurant.isServiceChargeEnabled(),
                "serviceChargeRate", restaurant.getServiceChargeRate()
        ));
    }
}
