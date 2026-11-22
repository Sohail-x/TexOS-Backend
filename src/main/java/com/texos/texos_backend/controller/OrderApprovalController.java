package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.OrderApprovalAccessRequest;
import com.texos.texos_backend.dto.OrderApprovalAccessCodeResponse;
import com.texos.texos_backend.dto.OrderApprovalSettingsRequest;
import com.texos.texos_backend.dto.OrderApprovalSettingsResponse;
import com.texos.texos_backend.dto.OrderApprovalStaffSessionResponse;
import com.texos.texos_backend.dto.OrderApprovalPushConfigResponse;
import com.texos.texos_backend.dto.OrderApprovalPushSubscriptionRequest;
import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.service.OrderApprovalService;
import com.texos.texos_backend.service.OrderApprovalPushService;
import com.texos.texos_backend.service.OrderApprovalStaffPrincipal;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderApprovalController {

    private final OrderApprovalService orderApprovalService;
    private final OrderApprovalPushService pushService;

    public OrderApprovalController(
            OrderApprovalService orderApprovalService, OrderApprovalPushService pushService) {
        this.orderApprovalService = orderApprovalService;
        this.pushService = pushService;
    }

    @GetMapping("/api/restaurants/{restaurantId}/order-approval")
    public OrderApprovalSettingsResponse getSettings(
            @PathVariable Long restaurantId, Authentication authentication) {
        return orderApprovalService.getSettings(restaurantId, authentication.getName());
    }

    @PutMapping("/api/restaurants/{restaurantId}/order-approval")
    public OrderApprovalSettingsResponse setEnabled(
            @PathVariable Long restaurantId,
            @Valid @RequestBody OrderApprovalSettingsRequest request,
            Authentication authentication) {
        return orderApprovalService.setEnabled(
                restaurantId, authentication.getName(), request.enabled());
    }

    @PostMapping("/api/restaurants/{restaurantId}/order-approval/access-code")
    public ResponseEntity<OrderApprovalAccessCodeResponse> generateAccessCode(
            @PathVariable Long restaurantId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                orderApprovalService.generateAccessCode(restaurantId, authentication.getName()));
    }

    @DeleteMapping("/api/restaurants/{restaurantId}/order-approval/link")
    public OrderApprovalSettingsResponse revokeLink(
            @PathVariable Long restaurantId, Authentication authentication) {
        return orderApprovalService.revokeLink(restaurantId, authentication.getName());
    }

    @PostMapping({"/api/public/order-approval/verify", "/api/public/order-approval/access"})
    public ResponseEntity<OrderApprovalStaffSessionResponse> verifyAccessCode(
            @Valid @RequestBody OrderApprovalAccessRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderApprovalService.verifyAccessCode(request.code(), servletRequest.getRemoteAddr()));
    }

    @GetMapping("/api/public/order-approval/push-config")
    public OrderApprovalPushConfigResponse getPushConfig() {
        return pushService.getConfig();
    }

    @GetMapping("/api/order-approval/staff/me")
    public Map<String, Object> staffSession(Authentication authentication) {
        OrderApprovalStaffPrincipal principal =
                (OrderApprovalStaffPrincipal) authentication.getPrincipal();
        var session = orderApprovalService.findActiveSession(
                principal.sessionId(), principal.restaurantId());
        if (session == null) {
            return Map.of();
        }
        return Map.of(
                "restaurantId", principal.restaurantId(),
                "restaurantName", session.getRestaurant().getRestaurantName(),
                "expiresAt", session.getExpiresAt());
    }

    @GetMapping("/api/order-approval/staff/orders")
    public List<OrderResponse> getPendingOrders(Authentication authentication) {
        OrderApprovalStaffPrincipal principal =
                (OrderApprovalStaffPrincipal) authentication.getPrincipal();
        return orderApprovalService.getPendingOrders(principal.restaurantId());
    }

    @PostMapping("/api/order-approval/staff/push-subscriptions")
    public ResponseEntity<Void> subscribeToNotifications(
            @Valid @RequestBody OrderApprovalPushSubscriptionRequest request,
            Authentication authentication) {
        OrderApprovalStaffPrincipal principal =
                (OrderApprovalStaffPrincipal) authentication.getPrincipal();
        pushService.subscribe(principal.restaurantId(), principal.sessionId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/order-approval/staff/push-subscriptions")
    public ResponseEntity<Void> unsubscribeFromNotifications(
            @RequestParam String endpoint,
            Authentication authentication) {
        OrderApprovalStaffPrincipal principal =
                (OrderApprovalStaffPrincipal) authentication.getPrincipal();
        pushService.unsubscribe(principal.restaurantId(), principal.sessionId(), endpoint);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/order-approval/staff/orders/{orderId}/approve")
    public OrderResponse approve(
            @PathVariable Long orderId, Authentication authentication) {
        OrderApprovalStaffPrincipal principal =
                (OrderApprovalStaffPrincipal) authentication.getPrincipal();
        return orderApprovalService.decide(
                principal.restaurantId(), orderId, principal.sessionId(), true);
    }

    @PostMapping("/api/order-approval/staff/orders/{orderId}/reject")
    public OrderResponse reject(
            @PathVariable Long orderId, Authentication authentication) {
        OrderApprovalStaffPrincipal principal =
                (OrderApprovalStaffPrincipal) authentication.getPrincipal();
        return orderApprovalService.decide(
                principal.restaurantId(), orderId, principal.sessionId(), false);
    }

    @PostMapping("/api/order-approval/staff/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        OrderApprovalStaffPrincipal principal =
                (OrderApprovalStaffPrincipal) authentication.getPrincipal();
        orderApprovalService.logout(principal.sessionId(), principal.restaurantId());
        return ResponseEntity.noContent().build();
    }
}
