package com.texos.texos_backend.service;

import com.texos.texos_backend.repository.CustomerRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerSessionService {

    private final CustomerRepository customerRepository;

    public static final int SESSION_TIMEOUT_SECONDS = 60 * 24 * 60 * 60;

    private static final String SESSION_ATTRIBUTE =
            "TEXOS_CUSTOMER_SESSIONS";

    private static final String ORDER_ACCESS_ATTRIBUTE =
            "TEXOS_CUSTOMER_ORDERS";

    private static final String ORDER_SESSION_KEYS_ATTRIBUTE =
            "TEXOS_ORDER_SESSION_KEYS";

    @SuppressWarnings("unchecked")
    public void createSession(
            HttpServletRequest request,
            Long customerId,
            Long restaurantId) {

        HttpSession session = request.getSession(true);

        // Rotate the session ID to reduce session fixation risk.
        request.changeSessionId();

        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);

        Map<Long, CustomerSession> sessions =
                (Map<Long, CustomerSession>) session.getAttribute(
                        SESSION_ATTRIBUTE);

        if (sessions == null) {
            sessions = new HashMap<>();
        }

        sessions.put(
                restaurantId,
                new CustomerSession(customerId, restaurantId)
        );

        session.setAttribute(SESSION_ATTRIBUTE, sessions);
    }

    @SuppressWarnings("unchecked")
    public CustomerSession getActiveSession(
            HttpServletRequest request,
            Long restaurantId) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return null;
        }

        Map<Long, CustomerSession> sessions =
                (Map<Long, CustomerSession>) session.getAttribute(
                        SESSION_ATTRIBUTE);

        if (sessions == null) {
            return null;
        }

        CustomerSession customerSession = sessions.get(restaurantId);

        if (customerSession == null) {
            return null;
        }

        if (!customerRepository.existsByIdAndRestaurant_IdAndActiveTrueAndDeletedAtIsNull(
                customerSession.getCustomerId(), restaurantId)) {
            sessions.remove(restaurantId);
            session.setAttribute(SESSION_ATTRIBUTE, sessions);
            return null;
        }

        Instant expiryTime = customerSession.getLastActivityAt()
                .plus(Duration.ofDays(60));

        if (!Instant.now().isBefore(expiryTime)) {
            sessions.remove(restaurantId);
            session.setAttribute(SESSION_ATTRIBUTE, sessions);
            return null;
        }

        customerSession.updateActivity();
        session.setAttribute(SESSION_ATTRIBUTE, sessions);
        LocalDateTime activityNow = LocalDateTime.now(ZoneOffset.UTC);
        customerRepository.touchLastActivity(
                customerSession.getCustomerId(),
                activityNow.minusDays(1),
                activityNow);

        return customerSession;
    }

    @SuppressWarnings("unchecked")
    public String getOrderSessionKey(HttpServletRequest request, Long restaurantId, Long tableId) {
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
        Map<String, String> keys = (Map<String, String>) session.getAttribute(ORDER_SESSION_KEYS_ATTRIBUTE);
        if (keys == null) keys = new HashMap<>();
        String mapKey = restaurantId + ":" + tableId;
        String key = keys.get(mapKey);
        if (key == null) {
            key = java.util.UUID.randomUUID().toString();
            keys.put(mapKey, key);
            session.setAttribute(ORDER_SESSION_KEYS_ATTRIBUTE, keys);
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    public String rotateOrderSessionKey(HttpServletRequest request, Long restaurantId, Long tableId) {
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
        Map<String, String> keys = (Map<String, String>) session.getAttribute(ORDER_SESSION_KEYS_ATTRIBUTE);
        if (keys == null) keys = new HashMap<>();
        String mapKey = restaurantId + ":" + tableId;
        String key = java.util.UUID.randomUUID().toString();
        keys.put(mapKey, key);
        session.setAttribute(ORDER_SESSION_KEYS_ATTRIBUTE, keys);
        return key;
    }

    @SuppressWarnings("unchecked")
    public void registerOrderAccess(
            HttpServletRequest request,
            Long restaurantId,
            String orderNumber) {

        HttpSession session = request.getSession(true);
        if (session.isNew()) {
            request.changeSessionId();
        }
        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);

        Map<Long, Set<String>> orders =
                (Map<Long, Set<String>>) session.getAttribute(
                        ORDER_ACCESS_ATTRIBUTE);

        if (orders == null) {
            orders = new HashMap<>();
        }

        orders.computeIfAbsent(restaurantId, ignored -> new HashSet<>())
                .add(orderNumber);
        session.setAttribute(ORDER_ACCESS_ATTRIBUTE, orders);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getOrderNumbers(
            HttpServletRequest request,
            Long restaurantId) {

        HttpSession session = request.getSession(false);
        if (session == null) {
            return Set.of();
        }

        Map<Long, Set<String>> orders =
                (Map<Long, Set<String>>) session.getAttribute(
                        ORDER_ACCESS_ATTRIBUTE);

        if (orders == null || orders.get(restaurantId) == null) {
            return Set.of();
        }

        return Set.copyOf(orders.get(restaurantId));
    }

    public boolean hasOrderAccess(
            Map<String, Object> sessionAttributes,
            String orderNumber) {
        if (sessionAttributes == null || orderNumber == null) {
            return false;
        }
        Object orderAccess = sessionAttributes.get(ORDER_ACCESS_ATTRIBUTE);
        if (!(orderAccess instanceof Map<?, ?> orders)) {
            return false;
        }
        return orders.values().stream()
                .anyMatch(value -> value instanceof Set<?> orderNumbers
                        && orderNumbers.contains(orderNumber));
    }

    @SuppressWarnings("unchecked")
    public void logout(
            HttpServletRequest request,
            Long restaurantId) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return;
        }

        Map<Long, CustomerSession> sessions =
                (Map<Long, CustomerSession>) session.getAttribute(
                        SESSION_ATTRIBUTE);

        if (sessions == null) {
            return;
        }

        sessions.remove(restaurantId);
        session.setAttribute(SESSION_ATTRIBUTE, sessions);

        if (sessions.isEmpty()
                && session.getAttribute(ORDER_ACCESS_ATTRIBUTE) == null) {
            session.invalidate();
        }
    }
}
