package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.KitchenAccessCodeRequest;
import com.texos.texos_backend.dto.KitchenStaffSessionResponse;
import com.texos.texos_backend.dto.KitchenAccessStatusResponse;
import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.service.KitchenAccessService;
import com.texos.texos_backend.service.KitchenStaffPrincipal;
import com.texos.texos_backend.service.OrderService;
import com.texos.texos_backend.dto.UpdateOrderStatusRequest;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.exception.KitchenAccessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KitchenAccessController {

    private final KitchenAccessService kitchenAccessService;
    private final OrderService orderService;

    public KitchenAccessController(
            KitchenAccessService kitchenAccessService,
            OrderService orderService) {
        this.kitchenAccessService = kitchenAccessService;
        this.orderService = orderService;
    }

    @PostMapping("/api/restaurants/{restaurantId}/kitchen-access")
    public ResponseEntity<?> generateCode(
            @PathVariable Long restaurantId,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                kitchenAccessService.generateCode(restaurantId, authentication.getName()));
    }

    @GetMapping("/api/restaurants/{restaurantId}/kitchen-access")
    public KitchenAccessStatusResponse getStatus(
            @PathVariable Long restaurantId,
            Authentication authentication) {
        return kitchenAccessService.getStatus(restaurantId, authentication.getName());
    }

    @PostMapping("/api/restaurants/{restaurantId}/kitchen-access/revoke")
    public KitchenAccessStatusResponse revoke(
            @PathVariable Long restaurantId,
            Authentication authentication) {
        return kitchenAccessService.revoke(restaurantId, authentication.getName());
    }

    @PostMapping("/api/kitchen-staff/verify")
    public KitchenStaffSessionResponse verifyCode(
            @Valid @RequestBody KitchenAccessCodeRequest request,
            HttpServletRequest servletRequest) {
        return kitchenAccessService.redeemCode(
                request.getCode(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/api/kitchen-staff/me")
    public Map<String, Object> staffSession(Authentication authentication) {
        KitchenStaffPrincipal principal = (KitchenStaffPrincipal) authentication.getPrincipal();
        var grant = kitchenAccessService.findActiveSession(
                principal.sessionId(), principal.restaurantId());
        if (grant == null) {
            return Map.of();
        }
        return Map.of(
                "restaurantId", principal.restaurantId(),
                "restaurantName", grant.getRestaurant().getRestaurantName(),
                "expiresAt", grant.getSessionExpiresAt());
    }

    @GetMapping("/api/kitchen-staff/orders")
    public List<OrderResponse> getKitchenOrders(Authentication authentication) {
        KitchenStaffPrincipal principal = (KitchenStaffPrincipal) authentication.getPrincipal();
        return orderService.getKitchenOrdersForOwner(principal.restaurantId());
    }

    @GetMapping("/api/restaurants/{restaurantId}/kitchen/orders")
    public List<OrderResponse> getOwnerKitchenOrders(@PathVariable Long restaurantId) {
        return orderService.getKitchenOrdersForOwner(restaurantId);
    }

    @PatchMapping("/api/kitchen-staff/orders/{orderId}/status")
    public Object updateKitchenOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            Authentication authentication) {
        if (!List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)
                .contains(request.getStatus())) {
            throw new com.texos.texos_backend.exception.InvalidRequestException(
                    "Kitchen staff can only advance an order to Accepted, Preparing, or Ready.");
        }
        KitchenStaffPrincipal principal = (KitchenStaffPrincipal) authentication.getPrincipal();
        return orderService.updateOrderStatus(
                principal.restaurantId(), orderId, request.getStatus());
    }

    @PostMapping("/api/kitchen-staff/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        KitchenStaffPrincipal principal = (KitchenStaffPrincipal) authentication.getPrincipal();
        kitchenAccessService.logout(principal.sessionId(), principal.restaurantId());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(KitchenAccessException.class)
    public ResponseEntity<Map<String, String>> handleKitchenAccessException(
            KitchenAccessException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage()));
    }
}
