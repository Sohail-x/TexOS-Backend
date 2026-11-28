package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
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

class OwnerAndKitchenOrdersBulkLoadTest {

    @Test
    void ownerHistoryMapsBillsWithOneBulkBillQueryInExistingOrderOrder() {
        RestaurantOrder newest = order(1L, OrderStatus.NEW);
        RestaurantOrder older = order(2L, OrderStatus.SERVED);
        RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
        BillRepository bills = mock(BillRepository.class);
        when(orders.findAllOwnerOrdersWithDetails(
                42L, List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED)))
                .thenReturn(List.of(newest, older));
        when(bills.findAllWithOrderDetailsByOrder_IdIn(List.of(1L, 2L)))
                .thenReturn(List.of(bill(newest), bill(older)));

        List<OrderResponse> result = service(orders, bills).getOrdersForOwner(42L);

        assertEquals(List.of(1L, 2L), result.stream().map(OrderResponse::getId).toList());
        assertEquals("INV-1", result.get(0).getBill().getBillNumber());
        assertEquals("Tea", result.get(0).getBill().getItems().get(0).getItemName());
        verify(orders).findAllOwnerOrdersWithDetails(
                42L, List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED));
        verify(bills).findAllWithOrderDetailsByOrder_IdIn(List.of(1L, 2L));
        verifyNoMoreInteractions(orders, bills);
    }

    @Test
    void ownerHistoryIncludesOrdersWithoutBills() {
        RestaurantOrder order = order(3L, OrderStatus.NEW);
        RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
        BillRepository bills = mock(BillRepository.class);
        when(orders.findAllOwnerOrdersWithDetails(
                42L, List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED)))
                .thenReturn(List.of(order));
        when(bills.findAllWithOrderDetailsByOrder_IdIn(List.of(3L))).thenReturn(List.of());

        List<OrderResponse> result = service(orders, bills).getOrdersForOwner(42L);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getId());
        assertNull(result.get(0).getBill());
        verify(orders).findAllOwnerOrdersWithDetails(
                42L, List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED));
        verify(bills).findAllWithOrderDetailsByOrder_IdIn(List.of(3L));
        verifyNoMoreInteractions(orders, bills);
    }

    @Test
    void kitchenQueryIsRestaurantScopedStatusFilteredAndDoesNotLoadBills() {
        RestaurantOrder order = order(4L, OrderStatus.PREPARING);
        RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
        BillRepository bills = mock(BillRepository.class);
        when(orders.findKitchenOrdersWithDetails(
                42L,
                List.of(OrderStatus.NEW, OrderStatus.ACCEPTED,
                        OrderStatus.PREPARING, OrderStatus.READY),
                List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED)))
                .thenReturn(List.of(order));

        List<OrderResponse> result = service(orders, bills).getKitchenOrdersForOwner(42L);

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).getId());
        assertEquals(OrderStatus.PREPARING, result.get(0).getOrderStatus());
        assertEquals("Tea", result.get(0).getItems().get(0).getItemName());
        assertNull(result.get(0).getBill());
        verify(orders).findKitchenOrdersWithDetails(
                42L,
                List.of(OrderStatus.NEW, OrderStatus.ACCEPTED,
                        OrderStatus.PREPARING, OrderStatus.READY),
                List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED));
        verifyNoMoreInteractions(orders, bills);
    }

    private static OrderService service(
            RestaurantOrderRepository orders,
            BillRepository bills) {
        BillService billService = new BillService(
                bills, mock(OrderCalculationService.class), orders);
        return new OrderService(
                mock(RestaurantTableRepository.class),
                mock(MenuItemRepository.class),
                orders,
                billService,
                bills,
                mock(SimpMessagingTemplate.class),
                mock(CustomerRepository.class),
                mock(CustomerSessionService.class),
                mock(LoyaltyService.class),
                mock(OrderCalculationService.class),
                mock(ApplicationEventPublisher.class),
                mock(RestaurantRepository.class));
    }

    private static RestaurantOrder order(Long id, OrderStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName("Restaurant");
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T1");
        OrderItem item = new OrderItem();
        item.setItemName("Tea");
        item.setUnitPrice(new BigDecimal("10.00"));
        item.setQuantity(1);
        item.setLineTotal(new BigDecimal("10.00"));
        RestaurantOrder order = new RestaurantOrder();
        order.setId(id);
        order.setOrderNumber("order-" + id);
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setOrderStatus(status);
        order.setApprovalStatus(OrderApprovalStatus.NOT_REQUIRED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setCreatedAt(LocalDateTime.of(2026, 11, 25, 10, 0).minusMinutes(id));
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private static Bill bill(RestaurantOrder order) {
        Bill bill = new Bill();
        bill.setBillNumber("INV-" + order.getId());
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
