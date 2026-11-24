package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.OrderApprovalSettingsResponse;
import com.texos.texos_backend.dto.OrderApprovalStaffSessionResponse;
import com.texos.texos_backend.dto.OrderApprovalAccessCodeResponse;
import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.entity.OrderApprovalAccessCodeGrant;
import com.texos.texos_backend.entity.OrderApprovalCodeStatus;
import com.texos.texos_backend.entity.OrderApprovalSession;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.exception.ResourceConflictException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.OrderApprovalSessionRepository;
import com.texos.texos_backend.repository.OrderApprovalAccessCodeGrantRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderApprovalService {

    private static final long SESSION_HOURS = 12;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final long ATTEMPT_WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final RestaurantRepository restaurantRepository;
    private final RestaurantOrderRepository orderRepository;
    private final OrderApprovalSessionRepository sessionRepository;
    private final OrderApprovalAccessCodeGrantRepository accessCodeRepository;
    private final OrderApprovalCodeHasher codeHasher;
    private final OrderApprovalPushService pushService;
    private final JwtService jwtService;
    private final OrderService orderService;
    private final LoyaltyService loyaltyService;
    private final BillService billService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendOrigin;
    private final ConcurrentHashMap<String, AttemptWindow> attemptsByAddress = new ConcurrentHashMap<>();

    public OrderApprovalService(
            RestaurantRepository restaurantRepository,
            RestaurantOrderRepository orderRepository,
            OrderApprovalSessionRepository sessionRepository,
            OrderApprovalAccessCodeGrantRepository accessCodeRepository,
            OrderApprovalCodeHasher codeHasher,
            OrderApprovalPushService pushService,
            JwtService jwtService,
            OrderService orderService,
            LoyaltyService loyaltyService,
            BillService billService,
            SimpMessagingTemplate messagingTemplate,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.frontend-origin}") String frontendOrigin) {
        this.restaurantRepository = restaurantRepository;
        this.orderRepository = orderRepository;
        this.sessionRepository = sessionRepository;
        this.accessCodeRepository = accessCodeRepository;
        this.codeHasher = codeHasher;
        this.pushService = pushService;
        this.jwtService = jwtService;
        this.orderService = orderService;
        this.loyaltyService = loyaltyService;
        this.billService = billService;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public OrderApprovalSettingsResponse getSettings(Long restaurantId, String ownerIdentity) {
        Restaurant restaurant = getOwnedRestaurant(restaurantId, ownerIdentity, false);
        return toSettings(restaurant);
    }

    @Transactional
    public OrderApprovalSettingsResponse setEnabled(
            Long restaurantId, String ownerIdentity, boolean enabled) {
        Restaurant restaurant = getOwnedRestaurant(restaurantId, ownerIdentity, true);
        if (enabled) {
            restaurant.setOrderApprovalEnabled(true);
            restaurantRepository.save(restaurant);
            return toSettings(restaurant);
        }

        restaurant.setOrderApprovalEnabled(false);
        restaurantRepository.save(restaurant);
        accessCodeRepository.revokeActiveByRestaurantId(restaurantId);
        revokeSessions(restaurantId);
        pushService.deleteForRestaurant(restaurantId);
        List<RestaurantOrder> pendingOrders =
                orderRepository.findAllByRestaurant_IdAndApprovalStatusOrderByCreatedAtAsc(
                        restaurantId, OrderApprovalStatus.PENDING);
        for (RestaurantOrder order : pendingOrders) {
            order.setApprovalStatus(OrderApprovalStatus.NOT_REQUIRED);
            order.setApprovalUpdatedAt(LocalDateTime.now());
            order.setApprovalSessionId(null);
            billService.refreshBill(order);
            RestaurantOrder released = orderRepository.save(order);
            publishOwnerOrder(restaurantId, released);
        }
        eventPublisher.publishEvent(RestaurantLiveUpdateEvent.ordersChanged(restaurantId));
        eventPublisher.publishEvent(
                RestaurantLiveUpdateEvent.approvalChanged(restaurantId));
        return toSettings(restaurant);
    }

    @Transactional
    public OrderApprovalAccessCodeResponse generateAccessCode(Long restaurantId, String ownerIdentity) {
        Restaurant restaurant = getOwnedRestaurant(restaurantId, ownerIdentity, true);
        if (!restaurant.isOrderApprovalEnabled() || !restaurant.isActive()) {
            throw new ResourceConflictException("Enable order approval for an active restaurant before generating a code.");
        }
        accessCodeRepository.revokeActiveByRestaurantId(restaurantId);
        LocalDateTime now = LocalDateTime.now();
        String code;
        String codeHash;
        do {
            code = String.format(java.util.Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
            codeHash = codeHasher.hash("order-approval-code:" + code);
        } while (accessCodeRepository.existsByCodeHash(codeHash));
        OrderApprovalAccessCodeGrant grant = new OrderApprovalAccessCodeGrant();
        grant.setRestaurant(restaurant);
        grant.setCodeHash(codeHash);
        grant.setStatus(OrderApprovalCodeStatus.ACTIVE);
        grant.setExpiresAt(now.plusMinutes(5));
        accessCodeRepository.save(grant);
        return new OrderApprovalAccessCodeResponse(code, grant.getExpiresAt());
    }

    @Transactional
    public OrderApprovalSettingsResponse revokeLink(Long restaurantId, String ownerIdentity) {
        Restaurant restaurant = getOwnedRestaurant(restaurantId, ownerIdentity, true);
        accessCodeRepository.revokeActiveByRestaurantId(restaurantId);
        revokeSessions(restaurantId);
        pushService.deleteForRestaurant(restaurantId);
        eventPublisher.publishEvent(
                RestaurantLiveUpdateEvent.approvalChanged(restaurantId));
        return toSettings(restaurant);
    }

    @Transactional(noRollbackFor = ResourceConflictException.class)
    public OrderApprovalStaffSessionResponse verifyAccessCode(String code, String remoteAddress) {
        enforceAttemptLimit(remoteAddress);
        if (code == null || !code.matches("\\d{6}")) {
            throw new ResourceNotFoundException("Invalid order approval code.");
        }
        String codeHash = codeHasher.hash("order-approval-code:" + code);
        OrderApprovalAccessCodeGrant grant = accessCodeRepository.findByCodeHashForUpdate(codeHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid order approval code."));
        LocalDateTime now = LocalDateTime.now();
        if (grant.getStatus() == OrderApprovalCodeStatus.USED) {
            throw new ResourceConflictException("This order approval code has already been used.");
        }
        if (grant.getStatus() == OrderApprovalCodeStatus.REVOKED) {
            throw new ResourceConflictException("This order approval code is no longer available.");
        }
        if (grant.getStatus() == OrderApprovalCodeStatus.EXPIRED
                || !grant.getExpiresAt().isAfter(now)) {
            grant.setStatus(OrderApprovalCodeStatus.EXPIRED);
            accessCodeRepository.save(grant);
            throw new ResourceConflictException("This order approval code has expired.");
        }
        Restaurant restaurant = grant.getRestaurant();
        if (!restaurant.isActive() || !restaurant.isOrderApprovalEnabled()) {
            grant.setStatus(OrderApprovalCodeStatus.REVOKED);
            accessCodeRepository.save(grant);
            throw new ResourceConflictException("Order approval access is currently disabled.");
        }
        UUID sessionId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(SESSION_HOURS);
        OrderApprovalSession session = new OrderApprovalSession();
        session.setSessionId(sessionId);
        session.setRestaurant(restaurant);
        session.setAccessCodeHash(codeHash);
        session.setExpiresAt(expiresAt);
        sessionRepository.save(session);
        grant.setStatus(OrderApprovalCodeStatus.USED);
        grant.setUsedAt(now);
        grant.setSessionId(sessionId);
        accessCodeRepository.save(grant);
        clearAttempts(remoteAddress);
        String jwt = jwtService.generateOrderApprovalStaffToken(
                sessionId, restaurant.getId(), Timestamp.valueOf(expiresAt));
        return new OrderApprovalStaffSessionResponse(
                jwt, restaurant.getId(), restaurant.getRestaurantName(), expiresAt);
    }

    @Transactional(readOnly = true)
    public OrderApprovalSession findActiveSession(UUID sessionId, Long restaurantId) {
        return sessionRepository.findBySessionId(sessionId)
                .filter(session -> session.getRestaurant().getId().equals(restaurantId))
                .filter(session -> session.getRevokedAt() == null)
                .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now()))
                .filter(session -> session.getRestaurant().isActive())
                .filter(session -> session.getRestaurant().isOrderApprovalEnabled())
                .filter(session -> accessCodeRepository
                        .findByCodeHashAndSessionIdAndStatus(
                                session.getAccessCodeHash(),
                                sessionId,
                                OrderApprovalCodeStatus.USED)
                        .isPresent())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getPendingOrders(Long restaurantId) {
        return orderRepository
                .findAllByRestaurant_IdAndApprovalStatusOrderByCreatedAtAsc(
                        restaurantId, OrderApprovalStatus.PENDING)
                .stream()
                .map(orderService::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse decide(
            Long restaurantId, Long orderId, UUID sessionId, boolean approve) {
        RestaurantOrder order = orderRepository.findByIdAndRestaurantIdForApproval(orderId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        OrderApprovalSession session = findActiveSession(sessionId, restaurantId);
        if (session == null) {
            throw new ResourceNotFoundException("Order approval staff session is not active.");
        }
        if (order.getApprovalStatus() == (approve
                ? OrderApprovalStatus.APPROVED : OrderApprovalStatus.REJECTED)) {
            return orderService.toResponse(order);
        }
        if (order.getApprovalStatus() != OrderApprovalStatus.PENDING) {
            throw new ResourceConflictException("This order is no longer awaiting approval.");
        }
        if (order.getOrderStatus() != OrderStatus.NEW) {
            throw new ResourceConflictException("This order can no longer be approved.");
        }

        if (approve) {
            billService.refreshBill(order);
        } else {
            order.setOrderStatus(OrderStatus.CANCELLED);
        }
        order.setApprovalStatus(approve ? OrderApprovalStatus.APPROVED : OrderApprovalStatus.REJECTED);
        order.setApprovalUpdatedAt(LocalDateTime.now());
        order.setApprovalSessionId(sessionId);
        RestaurantOrder saved = orderRepository.save(order);
        loyaltyService.processOrderLifecycle(saved);
        OrderResponse response = orderService.toResponse(saved);
        if (approve) {
            messagingTemplate.convertAndSend("/topic/restaurants/" + restaurantId + "/orders", response);
        }
        messagingTemplate.convertAndSend("/topic/orders/" + saved.getOrderNumber(), response);
        eventPublisher.publishEvent(RestaurantLiveUpdateEvent.ordersChanged(restaurantId));
        eventPublisher.publishEvent(
                RestaurantLiveUpdateEvent.approvalChanged(restaurantId));
        return response;
    }

    @Transactional
    public void logout(UUID sessionId, Long restaurantId) {
        OrderApprovalSession session = sessionRepository.findBySessionId(sessionId).orElse(null);
        if (session != null && session.getRestaurant().getId().equals(restaurantId)
                && session.getRevokedAt() == null) {
            session.setRevokedAt(LocalDateTime.now());
            sessionRepository.save(session);
            pushService.deleteForSession(sessionId);
        }
    }

    private Restaurant getOwnedRestaurant(Long restaurantId, String ownerIdentity, boolean lock) {
        Long ownerId = restaurantRepository.findById(restaurantId)
                .filter(restaurant -> (restaurant.getOwner().getEmail() != null
                        && restaurant.getOwner().getEmail().equalsIgnoreCase(ownerIdentity)
                        || restaurant.getOwner().getPhone() != null
                        && restaurant.getOwner().getPhone().equals(ownerIdentity)))
                .map(restaurant -> restaurant.getOwner().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found."));
        return (lock
                ? restaurantRepository.findOwnedForUpdate(restaurantId, ownerId)
                : restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found."));
    }

    private OrderApprovalSettingsResponse toSettings(Restaurant restaurant) {
        String pageUrl = restaurant.isOrderApprovalEnabled()
                ? frontendOrigin + "/order-approval"
                : null;
        return new OrderApprovalSettingsResponse(restaurant.isOrderApprovalEnabled(), pageUrl);
    }

    private void revokeSessions(Long restaurantId) {
        sessionRepository.revokeActiveByRestaurantId(restaurantId);
    }

    private void publishOwnerOrder(Long restaurantId, RestaurantOrder order) {
        OrderResponse response = orderService.toResponse(order);
        messagingTemplate.convertAndSend("/topic/restaurants/" + restaurantId + "/orders", response);
        messagingTemplate.convertAndSend("/topic/orders/" + order.getOrderNumber(), response);
    }

    private void enforceAttemptLimit(String remoteAddress) {
        String addressHash = codeHasher.hash(
                "order-approval-address:" + (remoteAddress == null ? "unknown" : remoteAddress));
        long now = System.currentTimeMillis();
        AttemptWindow window = attemptsByAddress.compute(addressHash, (key, current) -> {
            if (current == null || now - current.startedAt >= ATTEMPT_WINDOW_MILLIS) {
                return new AttemptWindow(now, 0);
            }
            return current;
        });
        synchronized (window) {
            if (window.attempts >= MAX_CODE_ATTEMPTS) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many code attempts. Try again in 15 minutes.");
            }
            window.attempts++;
        }
        if (attemptsByAddress.size() > 10_000) {
            attemptsByAddress.entrySet().removeIf(
                    entry -> now - entry.getValue().startedAt >= ATTEMPT_WINDOW_MILLIS);
        }
    }

    private void clearAttempts(String remoteAddress) {
        String addressHash = codeHasher.hash(
                "order-approval-address:" + (remoteAddress == null ? "unknown" : remoteAddress));
        attemptsByAddress.remove(addressHash);
    }

    private static final class AttemptWindow {
        private final long startedAt;
        private int attempts;

        private AttemptWindow(long startedAt, int attempts) {
            this.startedAt = startedAt;
            this.attempts = attempts;
        }
    }
}
