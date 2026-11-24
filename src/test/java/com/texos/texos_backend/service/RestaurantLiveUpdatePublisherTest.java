package com.texos.texos_backend.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RestaurantLiveUpdatePublisherTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RestaurantLiveUpdatePublisher publisher =
            new RestaurantLiveUpdatePublisher(messagingTemplate);

    @Test
    void publishesOrderInvalidationToKitchenAndDashboardTopics() {
        publisher.publish(RestaurantLiveUpdateEvent.ordersChanged(42L));
        Map<String, String> payload = Map.of("type", "ORDERS_CHANGED");

        verify(messagingTemplate).convertAndSend(
                "/topic/restaurants/42/live-orders", (Object) payload);
        verify(messagingTemplate).convertAndSend(
                "/topic/restaurants/42/dashboard-updates", (Object) payload);
    }

    @Test
    void publishesOnlyAnInvalidationForPublicAnnouncements() {
        publisher.publish(RestaurantLiveUpdateEvent.announcementsChanged());

        verify(messagingTemplate).convertAndSend(
                "/topic/announcements",
                (Object) Map.of("type", "ANNOUNCEMENTS_CHANGED"));
    }
}
