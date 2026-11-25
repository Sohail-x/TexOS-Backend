package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.OfflineCollectionRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.RestaurantPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/payments")
@RequiredArgsConstructor
public class RestaurantPaymentController {
    private final RestaurantPaymentService paymentService;
    private final OwnerRepository ownerRepository;

    @GetMapping
    public List<Map<String, Object>> history(
            @PathVariable Long restaurantId, Authentication authentication) {
        return paymentService.getPaymentHistory(restaurantId, requireOwner(authentication).getId());
    }

    @PostMapping("/offline")
    public Map<String, Object> recordOfflineCollection(
            @PathVariable Long restaurantId,
            @Valid @RequestBody OfflineCollectionRequest request,
            Authentication authentication) {
        return paymentService.recordOfflinePayment(
                restaurantId, request.getOrderId(), requireOwner(authentication).getId(),
                request.getMethod());
    }

    @GetMapping("/notifications/next")
    public Map<String, Object> nextNotification(
            @PathVariable Long restaurantId, Authentication authentication) {
        long controllerStartedAtNanos = DashboardRequestMeasurements.startControllerTiming();
        try {
            Map<String, Object> notification = paymentService.getNextOwnerNotification(
                    restaurantId, requireOwner(authentication).getId());
            DashboardRequestMeasurements.Measurement measurement =
                    DashboardRequestMeasurements.current();
            if (measurement != null) {
                measurement.recordNotificationType(notification.get("type"));
            }
            return notification;
        } finally {
            DashboardRequestMeasurements.finishControllerTiming(controllerStartedAtNanos);
        }
    }

    @PostMapping("/notifications/{notificationId}/dismiss")
    public void dismissNotification(
            @PathVariable Long restaurantId,
            @PathVariable Long notificationId,
            Authentication authentication) {
        paymentService.dismissOwnerNotification(
                restaurantId, notificationId, requireOwner(authentication).getId());
    }

    @PostMapping("/notifications/{notificationId}/assign")
    public Map<String, Object> assignWaiter(
            @PathVariable Long restaurantId,
            @PathVariable Long notificationId,
            Authentication authentication) {
        return paymentService.assignWaiter(
                restaurantId, notificationId, requireOwner(authentication).getId());
    }

    @GetMapping("/{paymentTransactionId}/receipt")
    public Map<String, Object> receipt(
            @PathVariable Long restaurantId,
            @PathVariable Long paymentTransactionId,
            Authentication authentication) {
        return paymentService.getReceipt(
                restaurantId, paymentTransactionId, requireOwner(authentication).getId());
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
