package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.util.Set;

import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderItem;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.dto.CreateOrderRequest;
import com.texos.texos_backend.dto.OrderItemRequest;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class OrderServiceAccessTest {

    @Test
    void createsQrOrderUsingOneBulkMenuItemLookup() {
        RestaurantTableRepository tableRepository = mock(RestaurantTableRepository.class);
        MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        RestaurantOrderRepository orderRepository = mock(RestaurantOrderRepository.class);
        CustomerSessionService customerSessionService = mock(CustomerSessionService.class);
        OrderCalculationService calculationService = mock(OrderCalculationService.class);
        BillService billService = mock(BillService.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher =
                mock(org.springframework.context.ApplicationEventPublisher.class);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        restaurant.setRestaurantName("Test restaurant");
        RestaurantTable table = new RestaurantTable();
        table.setId(8L);
        table.setTableNumber("T1");
        table.setRestaurant(restaurant);
        when(tableRepository.findByQrTokenAndActiveTrue("qr")).thenReturn(Optional.of(table));
        when(restaurantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(restaurant));
        when(customerSessionService.getActiveSession(any(HttpServletRequest.class), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(null);
        when(customerSessionService.getOrderSessionKey(any(HttpServletRequest.class), org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(8L)))
                .thenReturn("session");
        when(orderRepository.existsByRestaurant_IdAndTable_IdAndOrderSessionKeyAndPaymentStatus(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq("session"),
                org.mockito.ArgumentMatchers.eq(com.texos.texos_backend.entity.PaymentStatus.PAID)))
                .thenReturn(false);

        MenuCategory category = new MenuCategory();
        category.setId(4L);
        category.setCategoryName("Mains");
        category.setActive(true);
        category.setRestaurant(restaurant);
        MenuItem first = menuItem(1L, "Soup", category);
        MenuItem second = menuItem(2L, "Rice", category);
        when(menuItemRepository.findAllWithCategoryAndRestaurantByIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(first, second));
        when(calculationService.calculateBill(org.mockito.ArgumentMatchers.any(RestaurantOrder.class)))
                .thenAnswer(invocation -> {
                    Bill bill = new Bill();
                    bill.setTotalAmount(new BigDecimal("30.00"));
                    return bill;
                });
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(RestaurantOrder.class)))
                .thenAnswer(invocation -> {
                    RestaurantOrder order = invocation.getArgument(0);
                    order.setId(12L);
                    return order;
                });

        OrderService service = new OrderService(
                tableRepository, menuItemRepository, orderRepository, billService,
                mock(BillRepository.class), messagingTemplate, mock(CustomerRepository.class),
                customerSessionService, mock(LoyaltyService.class), calculationService,
                eventPublisher, restaurantRepository);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setQrToken("qr");
        request.setItems(List.of(orderItemRequest(1L), orderItemRequest(2L)));

        var result = service.createOrder(request, mock(HttpServletRequest.class));

        assertEquals(2, result.getItems().size());
        assertEquals(new BigDecimal("30.00"), result.getTotalAmount());
        verify(menuItemRepository).findAllWithCategoryAndRestaurantByIdIn(List.of(1L, 2L));
        verify(menuItemRepository, org.mockito.Mockito.never()).findById(1L);
        verify(menuItemRepository, org.mockito.Mockito.never()).findById(2L);
    }

    @Test
    void ownerOrderResponseIncludesItemCustomization() {
        RestaurantTableRepository tableRepository = mock(RestaurantTableRepository.class);
        MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        RestaurantOrderRepository orderRepository = mock(RestaurantOrderRepository.class);
        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName("Test restaurant");
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("1");
        RestaurantOrder order = new RestaurantOrder();
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setOrderNumber("order-12");
        OrderItem item = new OrderItem();
        item.setItemName("Noodles");
        item.setCustomization("No peanuts");
        item.setQuantity(1);
        order.setItems(new ArrayList<>(Set.of(item)));

        OrderService orderService = new OrderService(
                tableRepository,
                menuItemRepository,
                orderRepository,
                mock(BillService.class),
                mock(BillRepository.class),
                mock(SimpMessagingTemplate.class),
                mock(CustomerRepository.class),
                mock(CustomerSessionService.class),
                mock(LoyaltyService.class),
                mock(OrderCalculationService.class),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(RestaurantRepository.class));

        assertEquals("No peanuts", orderService.toResponse(order).getItems().get(0).getCustomization());
    }

    @Test
    void servingAnOrderTriggersPointsLifecycleProcessing() {
        RestaurantTableRepository tableRepository = mock(RestaurantTableRepository.class);
        MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        RestaurantOrderRepository orderRepository = mock(RestaurantOrderRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerSessionService customerSessionService = mock(CustomerSessionService.class);
        LoyaltyService loyaltyService = mock(LoyaltyService.class);

        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        Customer customer = new Customer();
        customer.setId(9L);
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("1");
        RestaurantOrder order = new RestaurantOrder();
        order.setId(11L);
        order.setRestaurant(restaurant);
        order.setCustomer(customer);
        order.setTable(table);
        order.setOrderNumber("order-11");
        order.setOrderStatus(OrderStatus.READY);
        order.setItems(new ArrayList<>());
        when(orderRepository.findByIdAndRestaurantId(11L, 7L))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderService orderService = new OrderService(
                tableRepository,
                menuItemRepository,
                orderRepository,
                mock(BillService.class),
                mock(BillRepository.class),
                mock(SimpMessagingTemplate.class),
                customerRepository,
                customerSessionService,
                loyaltyService,
                mock(OrderCalculationService.class),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(RestaurantRepository.class));

        orderService.updateOrderStatus(7L, 11L, OrderStatus.SERVED);

        verify(loyaltyService).processOrderLifecycle(order);
    }

    @Test
    void customerCannotRetrieveOrderWithoutSessionAccess() {
        RestaurantTableRepository tableRepository =
                mock(RestaurantTableRepository.class);
        MenuItemRepository menuItemRepository =
                mock(MenuItemRepository.class);
        RestaurantOrderRepository orderRepository =
                mock(RestaurantOrderRepository.class);
        CustomerRepository customerRepository =
                mock(CustomerRepository.class);
        CustomerSessionService customerSessionService =
                mock(CustomerSessionService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        Restaurant restaurant = new Restaurant();
        restaurant.setId(7L);
        RestaurantOrder order = new RestaurantOrder();
        order.setRestaurant(restaurant);
        when(orderRepository.findByOrderNumber("private-order"))
                .thenReturn(Optional.of(order));
        when(customerSessionService.getOrderNumbers(request, 7L))
                .thenReturn(Set.of());
        when(customerSessionService.getActiveSession(request, 7L))
                .thenReturn(null);

        OrderService orderService = new OrderService(
                tableRepository,
                menuItemRepository,
                orderRepository,
                mock(BillService.class),
                mock(BillRepository.class),
                mock(SimpMessagingTemplate.class),
                customerRepository,
                customerSessionService,
                mock(LoyaltyService.class),
                mock(OrderCalculationService.class),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(RestaurantRepository.class));

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.getCustomerOrder("private-order", request));
    }

    private static MenuItem menuItem(Long id, String name, MenuCategory category) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setItemName(name);
        item.setPrice(new java.math.BigDecimal("15.00"));
        item.setAvailable(true);
        item.setCategory(category);
        return item;
    }

    private static OrderItemRequest orderItemRequest(Long id) {
        OrderItemRequest item = new OrderItemRequest();
        item.setItemId(id);
        item.setQuantity(1);
        return item;
    }
}
