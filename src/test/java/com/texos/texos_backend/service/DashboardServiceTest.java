package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.DashboardSummaryResponse;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.repository.DashboardOrderSummary;
import com.texos.texos_backend.repository.DashboardRestaurantIdentity;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private RestaurantOrderRepository orderRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private DashboardOrderSummary orderSummary;

    @Test
    void summaryUsesOneAggregateAndPreservesAllResponseValues() {
        Long restaurantId = 42L;
        DashboardRestaurantIdentity restaurant =
                org.mockito.Mockito.mock(DashboardRestaurantIdentity.class);
        when(restaurant.getId()).thenReturn(restaurantId);
        when(restaurant.getRestaurantName()).thenReturn("Test restaurant");

        when(restaurantRepository.findDashboardIdentityById(restaurantId))
                .thenReturn(Optional.of(restaurant));
        when(orderRepository.getDashboardOrderSummary(
                eq(restaurantId),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED)),
                eq(OrderStatus.NEW),
                eq(OrderStatus.PREPARING),
                eq(OrderStatus.READY),
                eq(OrderStatus.SERVED),
                eq(OrderStatus.CANCELLED),
                eq(PaymentStatus.PENDING),
                eq(PaymentStatus.PAID)))
                .thenReturn(orderSummary);
        when(orderSummary.getTotalOrders()).thenReturn(27L);
        when(orderSummary.getNewOrders()).thenReturn(2L);
        when(orderSummary.getPreparingOrders()).thenReturn(3L);
        when(orderSummary.getReadyOrders()).thenReturn(4L);
        when(orderSummary.getServedOrders()).thenReturn(15L);
        when(orderSummary.getCancelledOrders()).thenReturn(3L);
        when(orderSummary.getPendingPayments()).thenReturn(5L);
        when(orderSummary.getPaidOrders()).thenReturn(20L);
        when(orderSummary.getTotalRevenue()).thenReturn(new BigDecimal("1250.75"));

        DashboardSummaryResponse response = new DashboardService(
                orderRepository, restaurantRepository).getSummary(restaurantId);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        assertEquals(new DashboardSummaryResponse(
                restaurantId,
                "Test restaurant",
                today,
                27L,
                2L,
                3L,
                4L,
                15L,
                3L,
                5L,
                20L,
                new BigDecimal("1250.75")), response);

        verify(restaurantRepository).findDashboardIdentityById(restaurantId);
        verify(orderRepository).getDashboardOrderSummary(
                restaurantId,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED),
                OrderStatus.NEW,
                OrderStatus.PREPARING,
                OrderStatus.READY,
                OrderStatus.SERVED,
                OrderStatus.CANCELLED,
                PaymentStatus.PENDING,
                PaymentStatus.PAID);
        verifyNoMoreInteractions(orderRepository, restaurantRepository);
    }
}
