package com.texos.texos_backend.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RestaurantLiveUpdatePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void publish(RestaurantLiveUpdateEvent event) {
        Object payload = Map.of("type", event.type());
        messagingTemplate.convertAndSend(
                event.destination(),
                payload);
        if (event.destination().endsWith("/live-orders")) {
            messagingTemplate.convertAndSend(
                    event.destination().replace("/live-orders", "/dashboard-updates"),
                    payload);
        }
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void publishOrderApprovalPending(OrderApprovalPendingEvent event) {
        Object payload = Map.of("type", "ORDER_APPROVAL_CHANGED");
        messagingTemplate.convertAndSend(
                "/topic/restaurants/" + event.restaurantId() + "/order-approval",
                payload);
    }
}
