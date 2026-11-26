package com.texos.texos_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texos.texos_backend.entity.*;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.exception.ResourceConflictException;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;
import com.texos.texos_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private static final List<SubscriptionStatus> ENTITLED =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING);

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final TrialClaimRepository trialClaimRepository;
    private final RestaurantRepository restaurantRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;
    private final PhonePeGateway phonePeGateway;

    @Value("${app.phonepe.subscriptions.client-id:}")
    private String phonePeClientId;
    @Value("${app.phonepe.subscriptions.client-secret:}")
    private String phonePeClientSecret;
    @Value("${app.phonepe.subscriptions.client-version:1}")
    private int phonePeClientVersion;
    @Value("${app.phonepe.subscriptions.merchant-id:}")
    private String phonePeMerchantId;
    @Value("${app.phonepe.subscriptions.environment:SANDBOX}")
    private String phonePeEnvironment;
    @Value("${app.phonepe.subscriptions.webhook-key-id:}")
    private String phonePeWebhookKeyId;
    @Value("${app.phonepe.subscriptions.webhook-secret:}")
    private String phonePeWebhookSecret;
    @Value("${app.frontend-origin}")
    private String frontendOrigin;

    public List<SubscriptionPlan> getPlans() {
        return planRepository.findAllByActiveTrueOrderByPriceAsc();
    }

    public Map<String, Object> getStatus(Long restaurantId, Long ownerId) {
        long serviceStartedAtNanos = DashboardRequestMeasurements.startService();
        try {
            requireRestaurant(restaurantId, ownerId);
            Subscription subscription = subscriptionRepository
                    .findFirstByRestaurantIdOrderByCreatedAtDesc(restaurantId).orElse(null);
            LocalDateTime now = utcNow();
            String status = subscription == null ? "NONE" : subscription.getStatus().name();
            boolean entitled = subscription != null && ENTITLED.contains(subscription.getStatus())
                    && subscription.getEndDate() != null && subscription.getEndDate().isAfter(now);
            if (subscription != null && ENTITLED.contains(subscription.getStatus()) && !entitled) {
                status = "EXPIRED";
            }
            return Map.of(
                    "restaurantId", restaurantId,
                    "status", status,
                    "entitled", entitled,
                    "subscription", subscription == null ? Map.of() : subscriptionView(subscription));
        } finally {
            DashboardRequestMeasurements.recordServiceDuration(serviceStartedAtNanos);
        }
    }

    @Transactional
    public Map<String, Object> startTrial(Long restaurantId, Long ownerId) {
        Restaurant restaurant = restaurantRepository.findOwnedForUpdate(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        if (trialClaimRepository.existsById(restaurantId)) {
            throw new ResourceConflictException("The free trial has already been claimed for this restaurant");
        }
        if (hasValidSubscription(restaurantId)) {
            throw new ResourceConflictException("This restaurant already has an active subscription");
        }
        SubscriptionPlan plan = planRepository.findFirstByTrialPlanTrueAndActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active free trial plan is configured"));
        LocalDateTime start = utcNow();
        Subscription subscription = new Subscription();
        subscription.setRestaurant(restaurant);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.TRIALING);
        subscription.setStartDate(start);
        subscription.setEndDate(start.plusDays(plan.getDurationDays()));
        subscriptionRepository.save(subscription);

        TrialClaim claim = new TrialClaim();
        claim.setRestaurantId(restaurantId);
        claim.setSubscriptionId(subscription.getId());
        trialClaimRepository.save(claim);
        return subscriptionView(subscription);
    }

    @Transactional
    public Map<String, Object> createOrder(Long restaurantId, Long planId, Long ownerId) {
        Restaurant restaurant = requireRestaurant(restaurantId, ownerId);
        SubscriptionPlan plan = planRepository.findByIdAndActiveTrue(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));
        if (plan.isTrialPlan() || plan.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Use the free-trial endpoint for trial plans");
        }
        PhonePeMerchantCredentials credentials = subscriptionCredentials();

        LocalDateTime now = utcNow();
        SubscriptionPayment existingPayment = paymentRepository
                .findFirstByRestaurant_IdAndPaymentStatusInOrderByCreatedAtDesc(
                        restaurantId, List.of("CREATED", "PROCESSING", "PENDING", "UNKNOWN"))
                .orElse(null);
        if (existingPayment != null) {
            if (!existingPayment.getPlan().getId().equals(planId)) {
                throw new ResourceConflictException(
                        "A payment for another subscription plan is still unresolved.");
            }
            return subscriptionCheckoutView(existingPayment);
        }

        Subscription subscription = subscriptionRepository
                .findFirstByRestaurantIdAndStatusInOrderByEndDateDesc(restaurantId, ENTITLED)
                .filter(s -> s.getEndDate() != null && s.getEndDate().isAfter(now))
                .orElse(null);
        if (subscription == null) {
            subscription = new Subscription();
            subscription.setRestaurant(restaurant);
            subscription.setPlan(plan);
            subscription.setStatus(SubscriptionStatus.PENDING);
            subscriptionRepository.save(subscription);
        }

        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setRestaurant(restaurant);
        payment.setSubscription(subscription);
        payment.setPlan(plan);
        payment.setAmount(plan.getPrice());
        payment.setCurrency(plan.getCurrency());
        payment.setMerchantOrderId("TXOS-SUB-" + restaurantId + "-"
                + UUID.randomUUID().toString().replace("-", ""));
        payment.setPaymentStatus("PROCESSING");
        paymentRepository.saveAndFlush(payment);
        try {
            PhonePeGateway.CreatedPayment created = phonePeGateway.createPayment(
                    "texos-subscriptions",
                    credentials,
                    payment.getMerchantOrderId(),
                    plan.getPrice().movePointRight(2).longValueExact(),
                    frontendOrigin.replaceAll("/+$", "") + "/plans?restaurantId="
                            + restaurantId + "&phonepePayment=" + payment.getMerchantOrderId());
            payment.setProviderOrderId(created.providerOrderId());
            payment.setCheckoutUrl(created.checkoutUrl());
            payment.setPaymentStatus("PENDING");
        } catch (PhonePeGateway.PhonePeRequestException ex) {
            payment.setPaymentStatus(ex.getHttpStatus() >= 500 ? "UNKNOWN" : "FAILED");
        } catch (PhonePeGateway.PhonePeTransportException ex) {
            payment.setPaymentStatus("UNKNOWN");
        } catch (IllegalStateException ex) {
            payment.setPaymentStatus("UNKNOWN");
        }
        paymentRepository.save(payment);
        return subscriptionCheckoutView(payment);
    }

    @Transactional
    public Map<String, Object> verifyPayment(
            Long restaurantId, Long ownerId, String merchantOrderId) {
        requireRestaurant(restaurantId, ownerId);
        SubscriptionPayment payment = paymentRepository.findByMerchantOrderIdForUpdate(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        if (!payment.getRestaurant().getId().equals(restaurantId)) {
            throw new SecurityException("Payment order does not belong to this restaurant");
        }
        reconcileSubscriptionPayment(payment);
        return subscriptionPaymentView(payment);
    }

    @Transactional
    public void processPhonePeWebhook(String rawBody, String keyId, String signature) {
        if (phonePeWebhookKeyId == null || phonePeWebhookKeyId.isBlank()
                || phonePeWebhookSecret == null || phonePeWebhookSecret.isBlank()) {
            throw new IllegalStateException("TexOS PhonePe subscription webhook is not configured");
        }
        phonePeGateway.verifyWebhookSignature(
                rawBody, keyId, signature, phonePeWebhookKeyId, phonePeWebhookSecret);
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid PhonePe subscription webhook payload", ex);
        }
        String eventType = root.path("event").asText("");
        String merchantOrderId = root.path("payload").path("merchantOrderId").asText("");
        String payloadMerchantId = root.path("payload").path("merchantId").asText("");
        if (!List.of("checkout.order.completed", "checkout.order.failed").contains(eventType)
                || merchantOrderId.isBlank()) {
            throw new IllegalArgumentException("Unsupported PhonePe subscription webhook event");
        }
        if (!payloadMerchantId.isBlank() && !phonePeMerchantId.equals(payloadMerchantId)) {
            throw new SecurityException("PhonePe webhook merchant does not match TexOS subscriptions");
        }
        SubscriptionPayment payment = paymentRepository.findByMerchantOrderIdForUpdate(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PhonePe subscription payment not found"));
        String eventId = phonePeGateway.sha256(rawBody);
        if (webhookEventRepository.existsByProviderEventId(eventId)) return;
        WebhookEvent event = new WebhookEvent();
        event.setProviderEventId(eventId);
        event.setEventType(eventType);
        event.setProcessingStatus("PROCESSING");
        webhookEventRepository.save(event);
        reconcileSubscriptionPayment(payment);
        event.setProcessingStatus("PROCESSED");
        event.setProcessedAt(utcNow());
        webhookEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public boolean isKnownPhonePeOrder(String merchantOrderId) {
        return paymentRepository.findByMerchantOrderId(merchantOrderId).isPresent();
    }

    @Transactional(readOnly = true)
    public List<String> getUnresolvedSubscriptionMerchantOrderIds() {
        return paymentRepository
                .findTop100ByPaymentStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        List.of("CREATED", "PROCESSING", "PENDING", "UNKNOWN"),
                        utcNow().minusMinutes(1))
                .stream()
                .map(SubscriptionPayment::getMerchantOrderId)
                .filter(orderId -> orderId != null && !orderId.isBlank())
                .toList();
    }

    @Transactional
    public void reconcileSubscriptionPayment(String merchantOrderId) {
        SubscriptionPayment payment = paymentRepository.findByMerchantOrderIdForUpdate(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        reconcileSubscriptionPayment(payment);
    }

    @Transactional
    public Map<String, Object> getPaymentStatus(
            Long restaurantId, Long ownerId, String merchantOrderId) {
        requireRestaurant(restaurantId, ownerId);
        SubscriptionPayment payment = paymentRepository.findByMerchantOrderIdForUpdate(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        if (!payment.getRestaurant().getId().equals(restaurantId)) {
            throw new SecurityException("Payment order does not belong to this restaurant");
        }
        if (!"CAPTURED".equals(payment.getPaymentStatus())) reconcileSubscriptionPayment(payment);
        return subscriptionPaymentView(payment);
    }

    public List<Map<String, Object>> getHistory(Long restaurantId, Long ownerId) {
        requireRestaurant(restaurantId, ownerId);
        return subscriptionRepository.findAllByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream().map(this::subscriptionView).toList();
    }

    public List<Map<String, Object>> getPayments(Long restaurantId, Long ownerId) {
        requireRestaurant(restaurantId, ownerId);
        return paymentRepository.findAllByRestaurantIdOrderByCreatedAtDesc(restaurantId).stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "amount", p.getAmount(),
                        "currency", p.getCurrency(),
                        "status", p.getPaymentStatus(),
                        "orderId", p.getMerchantOrderId() == null ? "" : p.getMerchantOrderId(),
                        "paymentId", p.getProviderPaymentId() == null ? "" : p.getProviderPaymentId(),
                        "createdAt", p.getCreatedAt()))
                .toList();
    }

    public boolean hasValidSubscription(Long restaurantId) {
        LocalDateTime now = utcNow();
        List<LocalDateTime> endDates = subscriptionRepository
                .findEndDatesByRestaurantIdAndStatusIn(
                        restaurantId, ENTITLED, PageRequest.of(0, 1));
        return !endDates.isEmpty()
                && endDates.get(0) != null
                && endDates.get(0).isAfter(now);
    }

    public boolean hasAnyValidSubscriptionForOwner(Long ownerId) {
        return subscriptionRepository.existsValidSubscriptionForOwner(ownerId, ENTITLED, utcNow());
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    public void expireOverdueSubscriptions() {
        subscriptionRepository.expireOverdue(ENTITLED, utcNow());
    }

    private void activatePayment(
            SubscriptionPayment payment, String paymentId, LocalDateTime providerPaidAt) {
        if ("CAPTURED".equals(payment.getPaymentStatus())) return;
        LocalDateTime confirmedAt = providerPaidAt == null ? utcNow() : providerPaidAt;
        Subscription subscription = payment.getSubscription();
        LocalDateTime currentExpiry = subscription.getEndDate();
        boolean renewal = subscription.getStatus() == SubscriptionStatus.ACTIVE
                || subscription.getStatus() == SubscriptionStatus.TRIALING;
        subscription.setEndDate(calculateEndDate(
                renewal, currentExpiry, payment.getPlan().getDurationDays(), confirmedAt));
        if (!renewal || currentExpiry == null || !currentExpiry.isAfter(confirmedAt)) {
            subscription.setStartDate(confirmedAt);
        }
        subscription.setPlan(payment.getPlan());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        payment.setProviderPaymentId(paymentId);
        payment.setProviderVerified(true);
        payment.setPaymentStatus("CAPTURED");
        paymentRepository.save(payment);
        subscriptionRepository.save(subscription);
    }

    private void reconcileSubscriptionPayment(SubscriptionPayment payment) {
        if ("CAPTURED".equals(payment.getPaymentStatus())) return;
        if (payment.getMerchantOrderId() == null || payment.getMerchantOrderId().isBlank()) {
            throw new ResourceNotFoundException("PhonePe merchant order ID is unavailable");
        }
        PhonePeGateway.VerifiedPayment status = phonePeGateway.getOrderStatus(
                "texos-subscriptions",
                subscriptionCredentials(),
                payment.getMerchantOrderId(),
                payment.getAmount().movePointRight(2).longValueExact());
        if (!status.merchantId().isBlank() && !phonePeMerchantId.equals(status.merchantId())) {
            throw new SecurityException("PhonePe subscription merchant does not match TexOS");
        }
        if (!status.providerOrderId().isBlank() && payment.getProviderOrderId() != null
                && !payment.getProviderOrderId().equals(status.providerOrderId())) {
            throw new SecurityException("PhonePe subscription order does not match");
        }
        if ("COMPLETED".equals(status.state())) {
            if (status.amountMinor() != payment.getAmount().movePointRight(2).longValueExact()) {
                throw new SecurityException("PhonePe subscription payment amount does not match");
            }
            activatePayment(payment, status.paymentReference(), status.paidAt());
        } else if ("FAILED".equals(status.state()) || "CANCELLED".equals(status.state())) {
            payment.setPaymentStatus(status.state());
            paymentRepository.save(payment);
        } else if ("PENDING".equals(status.state()) || "PROCESSING".equals(status.state())) {
            payment.setPaymentStatus(status.state());
            paymentRepository.save(payment);
        } else {
            payment.setPaymentStatus("UNKNOWN");
            paymentRepository.save(payment);
        }
    }

    private PhonePeMerchantCredentials subscriptionCredentials() {
        if (phonePeClientId == null || phonePeClientId.isBlank()
                || phonePeClientSecret == null || phonePeClientSecret.isBlank()
                || phonePeMerchantId == null || phonePeMerchantId.isBlank()) {
            throw new IllegalStateException("TexOS PhonePe subscription credentials are not configured");
        }
        return new PhonePeMerchantCredentials(
                phonePeMerchantId, phonePeClientId, phonePeClientSecret,
                phonePeClientVersion, phonePeEnvironment);
    }

    private Map<String, Object> subscriptionCheckoutView(SubscriptionPayment payment) {
        return Map.of(
                "paymentId", payment.getId(),
                "orderId", payment.getMerchantOrderId() == null ? "" : payment.getMerchantOrderId(),
                "merchantOrderId", payment.getMerchantOrderId() == null ? "" : payment.getMerchantOrderId(),
                "providerOrderId", payment.getProviderOrderId() == null ? "" : payment.getProviderOrderId(),
                "checkoutUrl", payment.getCheckoutUrl() == null ? "" : payment.getCheckoutUrl(),
                "amount", payment.getAmount().movePointRight(2).longValueExact(),
                "currency", payment.getCurrency(),
                "planName", payment.getPlan().getName(),
                "status", payment.getPaymentStatus());
    }

    private Map<String, Object> subscriptionPaymentView(SubscriptionPayment payment) {
        return Map.of(
                "paymentId", payment.getId(),
                "merchantOrderId", payment.getMerchantOrderId() == null ? "" : payment.getMerchantOrderId(),
                "status", "CAPTURED".equals(payment.getPaymentStatus())
                        ? "SUCCESSFUL"
                        : payment.getPaymentStatus(),
                "paymentStatus", payment.getPaymentStatus(),
                "providerPaymentId", payment.getProviderPaymentId() == null ? "" : payment.getProviderPaymentId(),
                "subscription", subscriptionView(payment.getSubscription()));
    }

    private Restaurant requireRestaurant(Long restaurantId, Long ownerId) {
        return restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
    }

    private Map<String, Object> subscriptionView(Subscription s) {
        return Map.of(
                "id", s.getId(),
                "restaurantId", s.getRestaurant().getId(),
                "planId", s.getPlan().getId(),
                "planName", s.getPlan().getName(),
                "amount", s.getPlan().getPrice(),
                "currency", s.getPlan().getCurrency(),
                "durationDays", s.getPlan().getDurationDays(),
                "status", s.getStatus().name(),
                "startDate", s.getStartDate() == null ? "" : s.getStartDate(),
                "endDate", s.getEndDate() == null ? "" : s.getEndDate());
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    static LocalDateTime calculateEndDate(
            boolean renewal, LocalDateTime currentExpiry, int durationDays, LocalDateTime confirmedAt) {
        if (renewal && currentExpiry != null && currentExpiry.isAfter(confirmedAt)) {
            return currentExpiry.plusDays(durationDays);
        }
        return confirmedAt.plusDays(durationDays);
    }
}
