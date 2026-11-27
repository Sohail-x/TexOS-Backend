package com.texos.texos_backend.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.KitchenAccessGrant;
import com.texos.texos_backend.entity.OrderApprovalSession;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.service.CustomerSessionService;
import com.texos.texos_backend.service.KitchenAccessService;
import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.service.OrderApprovalService;
import com.texos.texos_backend.service.SubscriptionService;
import java.security.Principal;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

class WebSocketSubscriptionAuthorizationInterceptorTest {
    private static final String JWT_SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private final CustomerSessionService customerSessionService =
            new CustomerSessionService(mock(CustomerRepository.class));
    private final JwtService jwtService = new JwtService(JWT_SECRET);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final KitchenAccessService kitchenAccessService = mock(KitchenAccessService.class);
    private final OrderApprovalService orderApprovalService = mock(OrderApprovalService.class);
    private final ObjectProvider<OrderApprovalService> orderApprovalServiceProvider =
            new ObjectProvider<>() {
                @Override
                public OrderApprovalService getObject() {
                    return orderApprovalService;
                }
            };
    private final WebSocketSubscriptionAuthorizationInterceptor interceptor =
            new WebSocketSubscriptionAuthorizationInterceptor(
                    customerSessionService,
                    jwtService,
                    ownerRepository,
                    restaurantRepository,
                    subscriptionService,
                    kitchenAccessService,
                    orderApprovalServiceProvider);

    @Test
    void preservesCustomerOrderAuthorizationAndAllowsPublicAnnouncements() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        customerSessionService.registerOrderAccess(request, 42L, "TX-#104");
        Map<String, Object> sessionAttributes = copySessionAttributes(request);
        Message<byte[]> authorized = message(
                StompCommand.SUBSCRIBE, "/topic/orders/TX-#104", sessionAttributes, null);

        assertSame(authorized, interceptor.preSend(authorized, null));
        Message<byte[]> announcements =
                message(StompCommand.SUBSCRIBE, "/topic/announcements", null, null);
        assertSame(announcements, interceptor.preSend(announcements, null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/orders/TX-#105", sessionAttributes, null),
                null));
    }

    @Test
    void ownerConnectCanSubscribeOnlyToOwnedAndEntitledRestaurantTopics() {
        Owner owner = owner(7L, "owner@example.test", 3);
        when(ownerRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(restaurantRepository.findByIdAndOwnerId(42L, owner.getId()))
                .thenReturn(Optional.of(new Restaurant()));
        when(subscriptionService.hasValidSubscription(42L)).thenReturn(true);
        String token = jwtService.generateToken(owner.getEmail(), owner.getTokenVersion());

        Principal principal = connectAs(token);
        Message<byte[]> liveOrders = message(
                StompCommand.SUBSCRIBE, "/topic/restaurants/42/live-orders", null, principal);
        Message<byte[]> dashboard = message(
                StompCommand.SUBSCRIBE, "/topic/restaurants/42/dashboard-updates", null, principal);

        assertSame(liveOrders, interceptor.preSend(liveOrders, null));
        assertSame(dashboard, interceptor.preSend(dashboard, null));
        verify(restaurantRepository, times(2)).findByIdAndOwnerId(42L, owner.getId());
        verify(subscriptionService, times(2)).hasValidSubscription(42L);
    }

    @Test
    void ownerCannotSubscribeToAnotherRestaurantOrWithoutAnAuthenticatedConnect() {
        Owner owner = owner(7L, "owner@example.test", 3);
        when(ownerRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(restaurantRepository.findByIdAndOwnerId(99L, owner.getId()))
                .thenReturn(Optional.empty());
        Principal principal = connectAs(
                jwtService.generateToken(owner.getEmail(), owner.getTokenVersion()));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/restaurants/99/live-orders", null, principal),
                null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/restaurants/42/dashboard-updates", null, null),
                null));
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void rejectsInactiveOwnersAndOwnerTokensWithAnOldVersion() {
        Owner inactiveOwner = owner(7L, "inactive@example.test", 3);
        inactiveOwner.setActive(false);
        when(ownerRepository.findByEmail(inactiveOwner.getEmail()))
                .thenReturn(Optional.of(inactiveOwner));
        String inactiveToken = jwtService.generateToken(
                inactiveOwner.getEmail(), inactiveOwner.getTokenVersion());

        assertThrows(AccessDeniedException.class, () -> connectAs(inactiveToken));

        Owner ownerWithNewerTokenVersion = owner(8L, "owner@example.test", 4);
        when(ownerRepository.findByEmail(ownerWithNewerTokenVersion.getEmail()))
                .thenReturn(Optional.of(ownerWithNewerTokenVersion));
        String oldToken = jwtService.generateToken(
                ownerWithNewerTokenVersion.getEmail(), 3);

        assertThrows(AccessDeniedException.class, () -> connectAs(oldToken));
        verifyNoInteractions(restaurantRepository, subscriptionService);
    }

    @Test
    void kitchenStaffCanSubscribeOnlyToTheirRestaurantOrders() {
        UUID sessionId = UUID.randomUUID();
        when(kitchenAccessService.findActiveSession(sessionId, 42L))
                .thenReturn(new KitchenAccessGrant());
        String token = jwtService.generateKitchenStaffToken(
                sessionId, 42L, new Date(System.currentTimeMillis() + 60_000));
        Principal principal = connectAs(token);
        Message<byte[]> orders = message(
                StompCommand.SUBSCRIBE, "/topic/restaurants/42/live-orders", null, principal);

        assertSame(orders, interceptor.preSend(orders, null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE,
                        "/topic/restaurants/43/live-orders", null, principal),
                null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE,
                        "/topic/restaurants/42/dashboard-updates", null, principal),
                null));
    }

    @Test
    void orderApproverCanSubscribeOnlyToItsApprovalQueue() {
        UUID sessionId = UUID.randomUUID();
        when(orderApprovalService.findActiveSession(sessionId, 42L))
                .thenReturn(new OrderApprovalSession());
        String token = jwtService.generateOrderApprovalStaffToken(
                sessionId, 42L, new Date(System.currentTimeMillis() + 60_000));
        Principal principal = connectAs(token);
        Message<byte[]> approval = message(
                StompCommand.SUBSCRIBE, "/topic/restaurants/42/order-approval", null, principal);

        assertSame(approval, interceptor.preSend(approval, null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE,
                        "/topic/restaurants/42/live-orders", null, principal),
                null));
    }

    @Test
    void rejectsPrivateTopicsWithoutAValidPrincipalAndClientPublishes() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/orders/TX-#104", null, null), null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SUBSCRIBE, "/topic/restaurants/42/order-approval", null, null),
                null));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                message(StompCommand.SEND, "/topic/orders/TX-#104", null, null), null));
    }

    private Principal connectAs(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        Message<byte[]> connect = MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());
        Message<?> authenticated = interceptor.preSend(connect, null);
        return StompHeaderAccessor.wrap(authenticated).getUser();
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
            Map<String, Object> sessionAttributes,
            Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Owner owner(Long id, String email, int tokenVersion) {
        Owner owner = new Owner();
        owner.setId(id);
        owner.setEmail(email);
        owner.setActive(true);
        owner.setTokenVersion(tokenVersion);
        return owner;
    }
}
