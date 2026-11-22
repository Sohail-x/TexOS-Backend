package com.texos.texos_backend.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.texos.texos_backend.service.CustomerSessionService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionAuthorizationInterceptor implements ChannelInterceptor {
    private static final String CUSTOMER_ORDER_TOPIC = "/topic/orders/";

    private final CustomerSessionService customerSessionService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == StompCommand.SEND) {
            throw new AccessDeniedException("Client messages are not allowed.");
        }
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(CUSTOMER_ORDER_TOPIC)) {
            throw new AccessDeniedException("Subscription is not authorized.");
        }
        String orderNumber = destination.substring(CUSTOMER_ORDER_TOPIC.length());
        if (orderNumber.isBlank() || orderNumber.contains("/")
                || !customerSessionService.hasOrderAccess(
                        accessor.getSessionAttributes(), orderNumber)) {
            throw new AccessDeniedException("Subscription is not authorized.");
        }
        return message;
    }
}
