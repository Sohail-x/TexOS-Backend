package com.texos.texos_backend.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import com.texos.texos_backend.service.CustomerSessionService;

class WebSocketSubscriptionAuthorizationInterceptorTest {
    private final CustomerSessionService customerSessionService = new CustomerSessionService();
    private final WebSocketSubscriptionAuthorizationInterceptor interceptor =
            new WebSocketSubscriptionAuthorizationInterceptor(customerSessionService);

    @Test
    void allowsOnlySubscriptionsToOrdersRegisteredInTheCustomerSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        customerSessionService.registerOrderAccess(request, 42L, "TX-#104");
        Map<String, Object> sessionAttributes = copySessionAttributes(request);
        Message<byte[]> authorized = message(
                StompCommand.SUBSCRIBE, "/topic/orders/TX-#104", sessionAttributes);

        assertSame(authorized, interceptor.preSend(authorized, null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/orders/TX-#105", sessionAttributes),
                null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/restaurants/42/orders", sessionAttributes),
                null));
    }

    @Test
    void rejectsOrderSubscriptionsWithoutCustomerSessionAndClientPublishes() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/orders/TX-#104", null), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SEND, "/topic/orders/TX-#104", null), null));
    }

    private Map<String, Object> copySessionAttributes(MockHttpServletRequest request) {
        Map<String, Object> attributes = new HashMap<>();
        Enumeration<String> names = request.getSession(false).getAttributeNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            attributes.put(name, request.getSession(false).getAttribute(name));
        }
        return attributes;
    }

    private Message<byte[]> message(
            StompCommand command,
            String destination,
            Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
