package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.OfflineCollectionRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.config.AuthenticatedOwnerPrincipal;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.RestaurantPaymentService;
import jakarta.servlet.http.HttpServletRequest;
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
            @PathVariable Long restaurantId,
            Authentication authentication,
            HttpServletRequest request) {
        return paymentService.getPaymentHistory(restaurantId, requireOwnerId(authentication, request));
    }

    @PostMapping("/offline")
    public Map<String, Object> recordOfflineCollection(
            @PathVariable Long restaurantId,
            @Valid @RequestBody OfflineCollectionRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return paymentService.recordOfflinePayment(
                restaurantId, request.getOrderId(), requireOwnerId(authentication, servletRequest),
                request.getMethod());
    }

    @GetMapping("/notifications/next")
    public Map<String, Object> nextNotification(
            @PathVariable Long restaurantId,
            Authentication authentication,
            HttpServletRequest request) {
        long controllerStartedAtNanos = DashboardRequestMeasurements.startControllerTiming();
        try {
            Map<String, Object> notification = paymentService.getNextOwnerNotification(
                    restaurantId, requireOwnerId(authentication, request));
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
            Authentication authentication,
            HttpServletRequest request) {
        paymentService.dismissOwnerNotification(
                restaurantId, notificationId, requireOwnerId(authentication, request));
    }

    @PostMapping("/notifications/{notificationId}/assign")
    public Map<String, Object> assignWaiter(
            @PathVariable Long restaurantId,
            @PathVariable Long notificationId,
            Authentication authentication,
            HttpServletRequest request) {
        return paymentService.assignWaiter(
                restaurantId, notificationId, requireOwnerId(authentication, request));
    }

    @GetMapping("/{paymentTransactionId}/receipt")
    public Map<String, Object> receipt(
            @PathVariable Long restaurantId,
            @PathVariable Long paymentTransactionId,
            Authentication authentication,
            HttpServletRequest request) {
        return paymentService.getReceipt(
                restaurantId, paymentTransactionId, requireOwnerId(authentication, request));
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
