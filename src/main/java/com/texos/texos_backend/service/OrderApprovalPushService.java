package com.texos.texos_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texos.texos_backend.dto.OrderApprovalPushConfigResponse;
import com.texos.texos_backend.dto.OrderApprovalPushSubscriptionRequest;
import com.texos.texos_backend.entity.OrderApprovalPushSubscription;
import com.texos.texos_backend.entity.OrderApprovalSession;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.OrderApprovalPushSubscriptionRepository;
import com.texos.texos_backend.repository.OrderApprovalAccessCodeGrantRepository;
import com.texos.texos_backend.repository.OrderApprovalSessionRepository;
import com.texos.texos_backend.entity.OrderApprovalCodeStatus;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class OrderApprovalPushService {

    private static final Logger LOGGER = Logger.getLogger(OrderApprovalPushService.class.getName());
    private static final List<String> ALLOWED_PUSH_HOSTS = List.of(
            "fcm.googleapis.com",
            "push.services.mozilla.com",
            "updates.push.services.mozilla.com",
            "wns.windows.com",
            "push.apple.com");
    private final OrderApprovalPushSubscriptionRepository subscriptionRepository;
    private final OrderApprovalSessionRepository sessionRepository;
    private final OrderApprovalAccessCodeGrantRepository accessCodeRepository;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private volatile PushService pushService;

    public OrderApprovalPushService(
            OrderApprovalPushSubscriptionRepository subscriptionRepository,
            OrderApprovalSessionRepository sessionRepository,
            OrderApprovalAccessCodeGrantRepository accessCodeRepository,
            ObjectMapper objectMapper,
            @Value("${app.webpush.public-key:}") String publicKey,
            @Value("${app.webpush.private-key:}") String privateKey,
            @Value("${app.webpush.subject:mailto:admin@texos.local}") String subject) {
        this.subscriptionRepository = subscriptionRepository;
        this.sessionRepository = sessionRepository;
        this.accessCodeRepository = accessCodeRepository;
        this.objectMapper = objectMapper;
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
        this.subject = subject;
        if (this.publicKey.isEmpty() != this.privateKey.isEmpty()) {
            throw new IllegalArgumentException("Both TEXOS_VAPID_PUBLIC_KEY and TEXOS_VAPID_PRIVATE_KEY are required.");
        }
    }

    public OrderApprovalPushConfigResponse getConfig() {
        return new OrderApprovalPushConfigResponse(isConfigured(), isConfigured() ? publicKey : "");
    }

    @Transactional
    public void subscribe(
            Long restaurantId,
            UUID sessionId,
            OrderApprovalPushSubscriptionRequest request) {
        OrderApprovalSession session = getActiveSession(sessionId, restaurantId);
        if (request.keys() == null) {
            throw new InvalidRequestException("Push subscription keys are required.");
        }
        String endpoint = validateEndpoint(request.endpoint());
        String endpointHash = sha256(endpoint);
        OrderApprovalPushSubscription subscription = subscriptionRepository
                .findByEndpointHash(endpointHash)
                .orElseGet(OrderApprovalPushSubscription::new);
        subscription.setSession(session);
        subscription.setEndpointHash(endpointHash);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuthKey(request.keys().auth());
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(Long restaurantId, UUID sessionId, String endpoint) {
        getActiveSession(sessionId, restaurantId);
        String validatedEndpoint = validateEndpoint(endpoint);
        subscriptionRepository.findByEndpointHash(sha256(validatedEndpoint))
                .filter(subscription -> subscription.getSession().getSessionId().equals(sessionId))
                .ifPresent(subscriptionRepository::delete);
    }

    @Transactional
    public void deleteForSession(UUID sessionId) {
        subscriptionRepository.deleteBySession_SessionId(sessionId);
    }

    @Transactional
    public void deleteForRestaurant(Long restaurantId) {
        subscriptionRepository.deleteBySession_Restaurant_Id(restaurantId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyNewPendingOrder(OrderApprovalPendingEvent event) {
        if (!isConfigured()) {
            return;
        }
        List<OrderApprovalPushSubscription> subscriptions =
                subscriptionRepository.findActiveForRestaurant(event.restaurantId(), LocalDateTime.now());
        if (subscriptions.isEmpty()) {
            return;
        }
        String body = "Table " + event.tableNumber() + " · "
                + java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("en-IN"))
                        .format(event.totalAmount());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "title", "New order needs approval",
                    "body", body,
                    "tag", "order-approval-" + event.orderId(),
                    "url", "/order-approval"));
        } catch (JsonProcessingException ex) {
            LOGGER.log(Level.SEVERE, "Could not serialize an order approval push notification.", ex);
            return;
        }
        CompletableFuture.runAsync(() -> subscriptions.forEach(subscription ->
                sendNotification(subscription, payload)));
    }

    private void sendNotification(OrderApprovalPushSubscription subscription, String payload) {
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuthKey(),
                    payload.getBytes(StandardCharsets.UTF_8),
                    60);
            var response = getPushService().send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.GONE.value()) {
                subscriptionRepository.deleteById(subscription.getId());
            } else if (status < 200 || status >= 300) {
                LOGGER.warning("Order approval push provider returned HTTP " + status + ".");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Order approval push delivery was interrupted.", ex);
        } catch (GeneralSecurityException | IOException | JoseException | ExecutionException ex) {
            LOGGER.log(Level.WARNING, "Could not deliver an order approval push notification.", ex);
        }
    }

    private PushService getPushService() throws GeneralSecurityException {
        PushService current = pushService;
        if (current == null) {
            synchronized (this) {
                current = pushService;
                if (current == null) {
                    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                        Security.addProvider(new BouncyCastleProvider());
                    }
                    current = new PushService(publicKey, privateKey, subject);
                    pushService = current;
                }
            }
        }
        return current;
    }

    private OrderApprovalSession getActiveSession(UUID sessionId, Long restaurantId) {
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
                .orElseThrow(() -> new ResourceNotFoundException("Order approval staff session is not active."));
    }

    private boolean isConfigured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }

    private String validateEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Push endpoint is invalid.");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || ALLOWED_PUSH_HOSTS.stream().noneMatch(allowed ->
                        host.equalsIgnoreCase(allowed) || host.toLowerCase(java.util.Locale.ROOT)
                                .endsWith("." + allowed))) {
            throw new InvalidRequestException("Push endpoint must use a supported HTTPS push provider.");
        }
        return uri.toASCIIString();
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not hash the push endpoint.", ex);
        }
    }
}
