package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.MenuPriceTaxMode;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.OrderItem;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class ActiveOrdersDashboardTest {

    @Test
    void activeOrdersBulkLoadBillsPreserveOrderAndExcludeApprovalRowsWithoutDuplicates() {
        RestaurantOrder newest = order(1L, "newest", OrderApprovalStatus.APPROVED, OrderStatus.NEW);
        RestaurantOrder pending = order(2L, "pending", OrderApprovalStatus.PENDING, OrderStatus.READY);
        RestaurantOrder rejected = order(3L, "rejected", OrderApprovalStatus.REJECTED, OrderStatus.PREPARING);
        RestaurantOrder older = order(4L, "older", OrderApprovalStatus.NOT_REQUIRED, OrderStatus.ACCEPTED);

        RestaurantOrderRepository orderRepository = mock(RestaurantOrderRepository.class);
        BillRepository billRepository = mock(BillRepository.class);
        when(orderRepository.findAllByRestaurant_IdAndOrderStatusInOrderByCreatedAtDesc(
                42L, List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)))
                .thenReturn(List.of(newest, newest, pending, rejected, older));
        when(billRepository.findAllByOrder_IdIn(List.of(1L, 4L)))
                .thenReturn(List.of(bill(older), bill(newest)));

        BillService billService = new BillService(billRepository, mock(OrderCalculationService.class));
        OrderService service = orderService(orderRepository, billRepository, billService);

        List<OrderResponse> responses = service.getActiveOrdersForOwner(42L);

        assertEquals(2, responses.size());
        assertEquals(List.of(1L, 4L), responses.stream().map(OrderResponse::getId).toList());
        assertEquals(List.of("newest", "older"),
                responses.stream().map(OrderResponse::getOrderNumber).toList());
        assertEquals("Test restaurant", responses.get(0).getRestaurantName());
        assertEquals("Table 1", responses.get(0).getTableNumber());
        assertEquals(OrderStatus.NEW, responses.get(0).getOrderStatus());
        assertEquals(OrderApprovalStatus.APPROVED, responses.get(0).getApprovalStatus());
        assertEquals(PaymentStatus.PAID, responses.get(0).getPaymentStatus());
        assertEquals(new BigDecimal("10.00"), responses.get(0).getTotalAmount());
        assertEquals("item-newest", responses.get(0).getItems().get(0).getItemName());
        assertEquals("No onions", responses.get(0).getItems().get(0).getCustomization());
        assertEquals("item-newest", responses.get(0).getBill().getItems().get(0).getItemName());
        assertEquals("INV-newest", responses.get(0).getBill().getBillNumber());
        assertEquals(1L, responses.get(0).getBill().getOrderId());
        assertEquals(PaymentStatus.PAID, responses.get(0).getBill().getPaymentStatus());
        assertEquals("item-older", responses.get(1).getItems().get(0).getItemName());
        assertEquals("INV-older", responses.get(1).getBill().getBillNumber());
        assertNotNull(responses.get(0).getCreatedAt());

        verify(orderRepository).findAllByRestaurant_IdAndOrderStatusInOrderByCreatedAtDesc(
                42L, List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY));
        verify(billRepository).findAllByOrder_IdIn(List.of(1L, 4L));
        verifyNoMoreInteractions(orderRepository, billRepository);
    }

    @Test
    void activeOrdersStillFailWhenAnOrderHasNoBill() {
        RestaurantOrder order = order(5L, "missing-bill", OrderApprovalStatus.NOT_REQUIRED, OrderStatus.READY);
        RestaurantOrderRepository orderRepository = mock(RestaurantOrderRepository.class);
        BillRepository billRepository = mock(BillRepository.class);
        when(orderRepository.findAllByRestaurant_IdAndOrderStatusInOrderByCreatedAtDesc(
                42L, List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)))
                .thenReturn(List.of(order));
        when(billRepository.findAllByOrder_IdIn(List.of(5L))).thenReturn(List.of());

        OrderService service = orderService(
                orderRepository,
                billRepository,
                new BillService(billRepository, mock(OrderCalculationService.class)));

        assertThrows(ResourceNotFoundException.class, () -> service.getActiveOrdersForOwner(42L));
        verify(orderRepository).findAllByRestaurant_IdAndOrderStatusInOrderByCreatedAtDesc(
                42L, List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY));
        verify(billRepository).findAllByOrder_IdIn(List.of(5L));
        verify(billRepository, never()).findByOrderOrderNumber("missing-bill");
        verifyNoMoreInteractions(orderRepository, billRepository);
    }

    private static OrderService orderService(
            RestaurantOrderRepository orderRepository,
            BillRepository billRepository,
            BillService billService) {
        return new OrderService(
                mock(RestaurantTableRepository.class),
                mock(MenuItemRepository.class),
                orderRepository,
                billService,
                billRepository,
                mock(SimpMessagingTemplate.class),
                mock(CustomerRepository.class),
                mock(CustomerSessionService.class),
                mock(LoyaltyService.class),
                mock(OrderCalculationService.class),
                mock(ApplicationEventPublisher.class),
                mock(RestaurantRepository.class));
    }

    private static RestaurantOrder order(
            Long id,
            String orderNumber,
            OrderApprovalStatus approvalStatus,
            OrderStatus orderStatus) {
        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName("Test restaurant");
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("Table 1");

        OrderItem item = new OrderItem();
        item.setItemName("item-" + orderNumber);
        item.setCustomization("No onions");
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setQuantity(1);
        item.setLineTotal(new BigDecimal("10.00"));

        RestaurantOrder order = new RestaurantOrder();
        order.setId(id);
        order.setOrderNumber(orderNumber);
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setOrderStatus(orderStatus);
        order.setApprovalStatus(approvalStatus);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setCreatedAt(LocalDateTime.of(2026, 11, 24, 10, 0).minusMinutes(id));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private static Bill bill(RestaurantOrder order) {
        Bill bill = new Bill();
        bill.setBillNumber("INV-" + order.getOrderNumber());
        bill.setOrder(order);
        bill.setSubtotal(new BigDecimal("10.00"));
        bill.setTaxMode(MenuPriceTaxMode.EXCLUSIVE);
        bill.setTaxRate(BigDecimal.ZERO);
        bill.setTaxAmount(BigDecimal.ZERO);
        bill.setServiceChargeRate(BigDecimal.ZERO);
        bill.setServiceChargeAmount(BigDecimal.ZERO);
        bill.setTotalAmount(new BigDecimal("10.00"));
        bill.setRewardDiscountAmount(BigDecimal.ZERO);
        return bill;
    }
}
