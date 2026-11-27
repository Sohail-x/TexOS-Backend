package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class OrderServiceDateFilterTest {

    @Test
    void appliesBothDateBoundsBeforePaginatingOwnerOrders() {
        RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
        OrderService service = service(orders);
        LocalDateTime from = LocalDateTime.of(2026, 11, 26, 0, 0);
        LocalDateTime to = from.plusDays(1);
        List<OrderApprovalStatus> excludedStatuses =
                List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED);
        when(orders.findOwnerOrderIdsPageInDateRange(
                7L, excludedStatuses, from, to, PageRequest.of(0, 100)))
                .thenReturn(List.of());

        assertTrue(service.getOrdersForOwner(7L, 0, 100, from, to).isEmpty());

        verify(orders).findOwnerOrderIdsPageInDateRange(
                7L, excludedStatuses, from, to, PageRequest.of(0, 100));
    }

    @Test
    void returnsNoOrdersForAnEmptyOrReversedDateRange() {
        RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
        OrderService service = service(orders);
        LocalDateTime from = LocalDateTime.of(2026, 11, 27, 0, 0);

        assertTrue(service.getOrdersForOwner(7L, 0, 100, from, from).isEmpty());
        assertTrue(service.getOrdersForOwner(7L, 0, 100, from, from.minusDays(1)).isEmpty());
    }

    private static OrderService service(RestaurantOrderRepository orders) {
        return new OrderService(
                mock(RestaurantTableRepository.class),
                mock(MenuItemRepository.class),
                orders,
                mock(BillService.class),
                mock(BillRepository.class),
                mock(SimpMessagingTemplate.class),
                mock(CustomerRepository.class),
                mock(CustomerSessionService.class),
                mock(LoyaltyService.class),
                mock(OrderCalculationService.class),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(RestaurantRepository.class));
    }
}
