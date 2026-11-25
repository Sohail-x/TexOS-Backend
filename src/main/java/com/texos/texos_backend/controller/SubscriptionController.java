package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.CreateSubscriptionOrderRequest;
import com.texos.texos_backend.dto.VerifySubscriptionPaymentRequest;
import com.texos.texos_backend.config.AuthenticatedOwnerPrincipal;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=60")
                .body(subscriptionService.getPlans());
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(
            @RequestParam Long restaurantId,
            Authentication authentication,
            HttpServletRequest request) {
        Long ownerId = requireOwnerId(authentication, request);
        return ResponseEntity.ok(subscriptionService.getStatus(restaurantId, ownerId));
    }

    @PostMapping("/trial")
    public ResponseEntity<?> startTrial(
            @RequestParam Long restaurantId,
            Authentication authentication,
            HttpServletRequest request) {
        Long ownerId = requireOwnerId(authentication, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.startTrial(restaurantId, ownerId));
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody CreateSubscriptionOrderRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        Long ownerId = requireOwnerId(authentication, servletRequest);
        return ResponseEntity.ok(subscriptionService.createOrder(
                request.getRestaurantId(), request.getPlanId(), ownerId));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @Valid @RequestBody VerifySubscriptionPaymentRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        Long ownerId = requireOwnerId(authentication, servletRequest);
        return ResponseEntity.ok(subscriptionService.verifyPayment(
                request.getRestaurantId(), ownerId, request.getMerchantOrderId()));
    }

    @GetMapping("/payment-status")
    public ResponseEntity<?> paymentStatus(
            @RequestParam Long restaurantId,
            @RequestParam String merchantOrderId,
            Authentication authentication,
            HttpServletRequest request) {
        Long ownerId = requireOwnerId(authentication, request);
        return ResponseEntity.ok(subscriptionService.getPaymentStatus(
                restaurantId, ownerId, merchantOrderId));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(
            @RequestParam Long restaurantId,
            Authentication authentication,
            HttpServletRequest request) {
        Long ownerId = requireOwnerId(authentication, request);
        return ResponseEntity.ok(Map.of(
                "subscriptions", subscriptionService.getHistory(restaurantId, ownerId),
                "payments", subscriptionService.getPayments(restaurantId, ownerId)));
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
