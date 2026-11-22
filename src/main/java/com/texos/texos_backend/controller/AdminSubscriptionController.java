package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.AdminGrantSubscriptionRequest;
import com.texos.texos_backend.service.AdminSubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class AdminSubscriptionController {
    private final AdminSubscriptionService adminSubscriptionService;

    @GetMapping("/restaurants")
    public List<Map<String, Object>> restaurants() {
        return adminSubscriptionService.listRestaurants();
    }

    @PostMapping("/restaurants/{restaurantId}/grant")
    public ResponseEntity<Map<String, Object>> grant(
            @PathVariable Long restaurantId,
            @Valid @RequestBody AdminGrantSubscriptionRequest request) {
        return ResponseEntity.ok(adminSubscriptionService.grantSubscription(restaurantId, request.getPlanId()));
    }

    @PutMapping("/restaurants/{restaurantId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(adminSubscriptionService.cancelSubscription(restaurantId));
    }
}
