package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderItem;
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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class OrderServiceAccessTest {

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
}
