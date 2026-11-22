package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.CreateSubscriptionOrderRequest;
import com.texos.texos_backend.dto.VerifySubscriptionPaymentRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final OwnerRepository ownerRepository;

    @GetMapping("/plans")
    public ResponseEntity<?> plans() {
        return ResponseEntity.ok(subscriptionService.getPlans());
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(
            @RequestParam Long restaurantId, Authentication authentication) {
        Owner owner = requireOwner(authentication);
        return ResponseEntity.ok(subscriptionService.getStatus(restaurantId, owner.getId()));
    }

    @PostMapping("/trial")
    public ResponseEntity<?> startTrial(
            @RequestParam Long restaurantId, Authentication authentication) {
        Owner owner = requireOwner(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.startTrial(restaurantId, owner.getId()));
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody CreateSubscriptionOrderRequest request,
            Authentication authentication) {
        Owner owner = requireOwner(authentication);
        return ResponseEntity.ok(subscriptionService.createOrder(
                request.getRestaurantId(), request.getPlanId(), owner.getId()));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @Valid @RequestBody VerifySubscriptionPaymentRequest request,
            Authentication authentication) {
        Owner owner = requireOwner(authentication);
        return ResponseEntity.ok(subscriptionService.verifyPayment(
                request.getRestaurantId(), owner.getId(), request.getMerchantOrderId()));
    }

    @GetMapping("/payment-status")
    public ResponseEntity<?> paymentStatus(
            @RequestParam Long restaurantId,
            @RequestParam String merchantOrderId,
            Authentication authentication) {
        Owner owner = requireOwner(authentication);
        return ResponseEntity.ok(subscriptionService.getPaymentStatus(
                restaurantId, owner.getId(), merchantOrderId));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(
            @RequestParam Long restaurantId, Authentication authentication) {
        Owner owner = requireOwner(authentication);
        return ResponseEntity.ok(Map.of(
                "subscriptions", subscriptionService.getHistory(restaurantId, owner.getId()),
                "payments", subscriptionService.getPayments(restaurantId, owner.getId())));
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
