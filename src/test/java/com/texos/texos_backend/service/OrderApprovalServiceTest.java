package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.OrderApprovalSession;
import com.texos.texos_backend.entity.OrderApprovalAccessCodeGrant;
import com.texos.texos_backend.entity.OrderApprovalCodeStatus;
import com.texos.texos_backend.exception.ResourceConflictException;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.repository.OrderApprovalSessionRepository;
import com.texos.texos_backend.repository.OrderApprovalAccessCodeGrantRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class OrderApprovalServiceTest {

    @Test
    void approvalAppliesRewardsOnceAndPublishesTheOrder() {
        Fixture fixture = new Fixture();

        fixture.service.decide(7L, 11L, fixture.sessionId, true);
        fixture.service.decide(7L, 11L, fixture.sessionId, true);

        assertEquals(OrderApprovalStatus.APPROVED, fixture.order.getApprovalStatus());
        verify(fixture.loyaltyService, times(1)).processOrderLifecycle(fixture.order);
        verify(fixture.billService, times(1)).refreshBill(fixture.order);
        verify(fixture.messagingTemplate, times(1)).convertAndSend(
                "/topic/restaurants/7/orders", fixture.response);
    }

    @Test
    void rejectionCancelsOrderWithoutApplyingRewardsOrSendingItToKitchen() {
        Fixture fixture = new Fixture();

        fixture.service.decide(7L, 11L, fixture.sessionId, false);

        assertEquals(OrderApprovalStatus.REJECTED, fixture.order.getApprovalStatus());
        assertEquals(OrderStatus.CANCELLED, fixture.order.getOrderStatus());
        verify(fixture.loyaltyService, times(1)).processOrderLifecycle(fixture.order);
        verify(fixture.billService, never()).refreshBill(fixture.order);
        verify(fixture.messagingTemplate, never()).convertAndSend(
                "/topic/restaurants/7/orders", fixture.response);
    }

    @Test
    void accessCodeCanOnlyBeRedeemedOnce() {
        Fixture fixture = new Fixture();
        OrderApprovalAccessCodeGrant grant = new OrderApprovalAccessCodeGrant();
        grant.setRestaurant(fixture.restaurant);
        grant.setCodeHash("approval-code-hash");
        grant.setStatus(OrderApprovalCodeStatus.ACTIVE);
        grant.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(fixture.codeHasher.hash("order-approval-code:123456"))
                .thenReturn("approval-code-hash");
        when(fixture.codeHasher.hash("order-approval-address:127.0.0.1"))
                .thenReturn("address-hash");
        when(fixture.accessCodeRepository.findByCodeHashForUpdate("approval-code-hash"))
                .thenReturn(Optional.of(grant));
        when(fixture.jwtService.generateOrderApprovalStaffToken(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn("staff-jwt");

        var session = fixture.service.verifyAccessCode("123456", "127.0.0.1");

        assertEquals("staff-jwt", session.token());
        assertEquals(OrderApprovalCodeStatus.USED, grant.getStatus());
        assertThrows(ResourceConflictException.class,
                () -> fixture.service.verifyAccessCode("123456", "127.0.0.1"));
    }

    @Test
    void generatedCodeIsSixDigitsAndExpiresWithinFiveMinutes() {
        Fixture fixture = new Fixture();
        Owner owner = new Owner();
        owner.setId(3L);
        owner.setEmail("owner@example.com");
        fixture.restaurant.setOwner(owner);
        when(fixture.restaurantRepository.findById(7L)).thenReturn(Optional.of(fixture.restaurant));
        when(fixture.restaurantRepository.findOwnedForUpdate(7L, 3L))
                .thenReturn(Optional.of(fixture.restaurant));
        when(fixture.codeHasher.hash(org.mockito.ArgumentMatchers.startsWith("order-approval-code:")))
                .thenReturn("generated-code-hash");
        when(fixture.accessCodeRepository.existsByCodeHash("generated-code-hash")).thenReturn(false);
        LocalDateTime before = LocalDateTime.now();

        var generated = fixture.service.generateAccessCode(7L, "owner@example.com");

        assertTrue(generated.code().matches("[0-9]{6}"));
        assertTrue(generated.expiresAt().isAfter(before));
        assertTrue(!generated.expiresAt().isAfter(before.plusMinutes(5).plusSeconds(1)));
        verify(fixture.accessCodeRepository).save(org.mockito.ArgumentMatchers.argThat(
                grant -> grant.getStatus() == OrderApprovalCodeStatus.ACTIVE
                        && grant.getCodeHash().equals("generated-code-hash")));
    }

    private static class Fixture {
        private final Restaurant restaurant = new Restaurant();
        private final Customer customer = new Customer();
        private final RestaurantOrder order = new RestaurantOrder();
        private final OrderResponse response = new OrderResponse();
        private final UUID sessionId = UUID.randomUUID();
        private final LoyaltyService loyaltyService = mock(LoyaltyService.class);
        private final BillService billService = mock(BillService.class);
        private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        private final RestaurantOrderRepository orderRepository = mock(RestaurantOrderRepository.class);
        private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        private final OrderApprovalAccessCodeGrantRepository accessCodeRepository =
                mock(OrderApprovalAccessCodeGrantRepository.class);
        private final OrderApprovalCodeHasher codeHasher = mock(OrderApprovalCodeHasher.class);
        private final JwtService jwtService = mock(JwtService.class);
        private final OrderApprovalService service;

        private Fixture() {
            restaurant.setId(7L);
            restaurant.setOrderApprovalEnabled(true);
            customer.setId(19L);
            order.setId(11L);
            order.setOrderNumber("TX-11");
            order.setRestaurant(restaurant);
            order.setCustomer(customer);
            order.setOrderStatus(OrderStatus.NEW);
            order.setApprovalStatus(OrderApprovalStatus.PENDING);

            OrderApprovalSession session = new OrderApprovalSession();
            session.setSessionId(sessionId);
            session.setRestaurant(restaurant);
            session.setAccessCodeHash("current-token");
            session.setExpiresAt(LocalDateTime.now().plusHours(1));

            OrderApprovalSessionRepository sessionRepository =
                    mock(OrderApprovalSessionRepository.class);
            when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
            when(accessCodeRepository.findByCodeHashAndSessionIdAndStatus(
                    "current-token", sessionId, OrderApprovalCodeStatus.USED))
                    .thenReturn(Optional.of(new OrderApprovalAccessCodeGrant()));
            when(orderRepository.findByIdAndRestaurantIdForApproval(11L, 7L))
                    .thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            OrderService orderService = mock(OrderService.class);
            when(orderService.toResponse(order)).thenReturn(response);
            service = new OrderApprovalService(
                    restaurantRepository,
                    orderRepository,
                    sessionRepository,
                    accessCodeRepository,
                    codeHasher,
                    mock(OrderApprovalPushService.class),
                    jwtService,
                    orderService,
                    loyaltyService,
                    billService,
                    messagingTemplate,
                    mock(org.springframework.context.ApplicationEventPublisher.class),
                    "http://localhost:5173");
        }
    }
}
