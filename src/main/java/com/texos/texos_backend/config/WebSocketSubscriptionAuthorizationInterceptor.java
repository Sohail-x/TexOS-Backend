package com.texos.texos_backend.config;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.service.KitchenAccessService;
import com.texos.texos_backend.service.KitchenStaffPrincipal;
import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.service.OrderApprovalService;
import com.texos.texos_backend.service.OrderApprovalStaffPrincipal;
import com.texos.texos_backend.service.SubscriptionService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.texos.texos_backend.service.CustomerSessionService;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionAuthorizationInterceptor implements ChannelInterceptor {
    private static final String CUSTOMER_ORDER_TOPIC = "/topic/orders/";
    private static final String RESTAURANT_TOPIC_PREFIX = "/topic/restaurants/";
    private static final String ANNOUNCEMENTS_TOPIC = "/topic/announcements";

    private final CustomerSessionService customerSessionService;
    private final JwtService jwtService;
    private final OwnerRepository ownerRepository;
    private final RestaurantRepository restaurantRepository;
    private final SubscriptionService subscriptionService;
    private final KitchenAccessService kitchenAccessService;
    private final ObjectProvider<OrderApprovalService> orderApprovalServiceProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == StompCommand.SEND) {
            throw new AccessDeniedException("Client messages are not allowed.");
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticateConnect(accessor);
            return MessageBuilder.createMessage(
                    message.getPayload(),
                    accessor.getMessageHeaders());
        }
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (ANNOUNCEMENTS_TOPIC.equals(destination)) {
            return message;
        }
        if (destination != null && destination.startsWith(CUSTOMER_ORDER_TOPIC)) {
            authorizeCustomerOrderSubscription(accessor, destination);
            return message;
        }
        if (destination != null && destination.startsWith(RESTAURANT_TOPIC_PREFIX)) {
            authorizeRestaurantSubscription(accessor, destination);
            return message;
        }
        throw new AccessDeniedException("Subscription is not authorized.");
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            return;
        }
        if (!header.startsWith("Bearer ") || header.length() <= "Bearer ".length()) {
            throw new AccessDeniedException("WebSocket authentication failed.");
        }

        try {
            JwtService.VerifiedTokenClaims claims =
                    jwtService.parseAuthenticationToken(header.substring("Bearer ".length()));
            String role = claims.role();
            if ("KITCHEN_STAFF".equals(role)) {
                authenticateKitchenStaff(claims, accessor);
            } else if ("ORDER_APPROVER".equals(role)) {
                authenticateOrderApprover(claims, accessor);
            } else if (role == null || "OWNER".equals(role)) {
                authenticateOwner(claims, accessor);
            } else {
                throw new AccessDeniedException("WebSocket authentication failed.");
            }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException exception) {
            throw new AccessDeniedException("WebSocket authentication failed.");
        }
    }

    private void authenticateOwner(
            JwtService.VerifiedTokenClaims claims,
            StompHeaderAccessor accessor) {
        String subject = claims.subject();
        if (subject == null) {
            throw new AccessDeniedException("WebSocket authentication failed.");
        }
        Owner owner = ownerRepository.findByEmail(subject.trim().toLowerCase())
                .or(() -> ownerRepository.findByPhone(subject))
                .orElse(null);
        if (owner == null || !owner.isActive()
                || !jwtService.isTokenValid(
                        claims,
                        owner.getEmail() == null || owner.getEmail().isBlank()
                                ? owner.getPhone() : owner.getEmail(),
                        owner.getTokenVersion())) {
            throw new AccessDeniedException("WebSocket authentication failed.");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new AuthenticatedOwnerPrincipal(owner.getId()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private void authenticateKitchenStaff(
            JwtService.VerifiedTokenClaims claims,
            StompHeaderAccessor accessor) {
        UUID sessionId = parseSessionId(claims.subject());
        Long restaurantId = claims.restaurantId();
        if (restaurantId == null
                || kitchenAccessService.findActiveSession(sessionId, restaurantId) == null) {
            throw new AccessDeniedException("WebSocket authentication failed.");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new KitchenStaffPrincipal(restaurantId, sessionId),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_KITCHEN_STAFF"))));
    }

    private void authenticateOrderApprover(
            JwtService.VerifiedTokenClaims claims,
            StompHeaderAccessor accessor) {
        UUID sessionId = parseSessionId(claims.subject());
        Long restaurantId = claims.restaurantId();
        if (restaurantId == null
                || orderApprovalServiceProvider.getObject()
                        .findActiveSession(sessionId, restaurantId) == null) {
            throw new AccessDeniedException("WebSocket authentication failed.");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new OrderApprovalStaffPrincipal(restaurantId, sessionId),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ORDER_APPROVER"))));
    }

    private UUID parseSessionId(String subject) {
        if (subject == null) {
            throw new AccessDeniedException("Subscription is not authorized.");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("WebSocket authentication failed.");
        }
    }

    private void authorizeCustomerOrderSubscription(
            StompHeaderAccessor accessor,
            String destination) {
        String orderNumber = destination.substring(CUSTOMER_ORDER_TOPIC.length());
        if (orderNumber.isBlank() || orderNumber.contains("/")
                || !customerSessionService.hasOrderAccess(
                        accessor.getSessionAttributes(), orderNumber)) {
            throw new AccessDeniedException("Subscription is not authorized.");
        }
    }

    private void authorizeRestaurantSubscription(
            StompHeaderAccessor accessor,
            String destination) {
        String suffix = destination.substring(RESTAURANT_TOPIC_PREFIX.length());
        String[] parts = suffix.split("/", -1);
        if (parts.length != 2 || !parts[0].matches("\\d+")) {
            throw new AccessDeniedException("Subscription is not authorized.");
        }

        Long restaurantId;
        try {
            restaurantId = Long.valueOf(parts[0]);
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Subscription is not authorized.");
        }

        Object principal = authenticatedPrincipal(accessor);
        switch (parts[1]) {
            case "live-orders" -> authorizeLiveOrders(principal, restaurantId);
            case "dashboard-updates" -> authorizeOwner(principal, restaurantId);
            case "order-approval" -> authorizeOrderApprover(principal, restaurantId);
            default -> throw new AccessDeniedException("Subscription is not authorized.");
        }
    }

    private Object authenticatedPrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.isAuthenticated()) {
            return authentication.getPrincipal();
        }
        return null;
    }

    private void authorizeLiveOrders(Object principal, Long restaurantId) {
        if (principal instanceof KitchenStaffPrincipal kitchenPrincipal
                && restaurantId.equals(kitchenPrincipal.restaurantId())
                && kitchenAccessService.findActiveSession(
                        kitchenPrincipal.sessionId(), restaurantId) != null) {
            return;
        }
        authorizeOwner(principal, restaurantId);
    }

    private void authorizeOwner(Object principal, Long restaurantId) {
        if (!(principal instanceof AuthenticatedOwnerPrincipal ownerPrincipal)
                || restaurantRepository.findByIdAndOwnerId(
                        restaurantId, ownerPrincipal.ownerId()).isEmpty()
                || !subscriptionService.hasValidSubscription(restaurantId)) {
            throw new AccessDeniedException("Subscription is not authorized.");
        }
    }

    private void authorizeOrderApprover(Object principal, Long restaurantId) {
        if (!(principal instanceof OrderApprovalStaffPrincipal approvalPrincipal)
                || !restaurantId.equals(approvalPrincipal.restaurantId())
                || orderApprovalServiceProvider.getObject().findActiveSession(
                        approvalPrincipal.sessionId(), restaurantId) == null) {
            throw new AccessDeniedException("Subscription is not authorized.");
        }
    }
}
