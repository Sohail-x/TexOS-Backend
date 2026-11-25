package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.dto.CreateOrderRequest;
import com.texos.texos_backend.dto.OrderItemRequest;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class OrderServiceBillSessionTest {
    private static final long RESTAURANT_ID = 1L;
    private static final long TABLE_ID = 2L;
    private static final long MENU_ITEM_ID = 3L;
    private static final String QR_TOKEN = "table-qr";
    private static final String EXISTING_SESSION_KEY = "existing-sitting";

    @Test
    void startsANewBillSessionWhenAnEarlierOrderWasPaid() {
        TestContext context = new TestContext();
        when(context.orderRepository
                .existsByRestaurant_IdAndTable_IdAndOrderSessionKeyAndPaymentStatus(
                        RESTAURANT_ID, TABLE_ID, EXISTING_SESSION_KEY, PaymentStatus.PAID))
                .thenReturn(true);
        when(context.customerSessionService.rotateOrderSessionKey(
                context.request, RESTAURANT_ID, TABLE_ID))
                .thenReturn("new-sitting");

        RestaurantOrder created = context.createOrder();

        assertEquals("new-sitting", created.getOrderSessionKey());
        verify(context.customerSessionService).rotateOrderSessionKey(
                context.request, RESTAURANT_ID, TABLE_ID);
    }

    @Test
    void keepsUsingTheExistingBillSessionWhileAllOrdersAreUnpaid() {
        TestContext context = new TestContext();
        when(context.orderRepository
                .existsByRestaurant_IdAndTable_IdAndOrderSessionKeyAndPaymentStatus(
                        RESTAURANT_ID, TABLE_ID, EXISTING_SESSION_KEY, PaymentStatus.PAID))
                .thenReturn(false);

        RestaurantOrder created = context.createOrder();

        assertEquals(EXISTING_SESSION_KEY, created.getOrderSessionKey());
        verify(context.customerSessionService, never()).rotateOrderSessionKey(
                context.request, RESTAURANT_ID, TABLE_ID);
    }

    private static class TestContext {
        private final RestaurantTableRepository tableRepository = mock(RestaurantTableRepository.class);
        private final MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
        private final RestaurantOrderRepository orderRepository = mock(RestaurantOrderRepository.class);
        private final BillService billService = mock(BillService.class);
        private final CustomerRepository customerRepository = mock(CustomerRepository.class);
        private final CustomerSessionService customerSessionService = mock(CustomerSessionService.class);
        private final OrderCalculationService calculationService = mock(OrderCalculationService.class);
        private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        private final HttpServletRequest request = mock(HttpServletRequest.class);
        private final Restaurant restaurant = new Restaurant();
        private final RestaurantTable table = new RestaurantTable();
        private RestaurantOrder createdOrder;
        private final OrderService service = new OrderService(
                tableRepository,
                menuItemRepository,
                orderRepository,
                billService,
                mock(BillRepository.class),
                mock(SimpMessagingTemplate.class),
                customerRepository,
                customerSessionService,
                mock(LoyaltyService.class),
                calculationService,
                mock(ApplicationEventPublisher.class),
                restaurantRepository);

        private TestContext() {
            restaurant.setId(RESTAURANT_ID);
            restaurant.setRestaurantName("Test restaurant");
            restaurant.setActive(true);
            table.setId(TABLE_ID);
            table.setTableNumber("T1");
            table.setRestaurant(restaurant);
            when(tableRepository.findByQrTokenAndActiveTrue(QR_TOKEN)).thenReturn(Optional.of(table));
            when(restaurantRepository.findByIdForUpdate(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
            when(customerSessionService.getOrderSessionKey(request, RESTAURANT_ID, TABLE_ID))
                    .thenReturn(EXISTING_SESSION_KEY);

            MenuCategory category = new MenuCategory();
            category.setActive(true);
            category.setRestaurant(restaurant);
            MenuItem menuItem = new MenuItem();
            menuItem.setId(MENU_ITEM_ID);
            menuItem.setItemName("Test item");
            menuItem.setPrice(new BigDecimal("10.00"));
            menuItem.setCategory(category);
            when(menuItemRepository.findAllWithCategoryAndRestaurantByIdIn(
                    List.of(MENU_ITEM_ID))).thenReturn(List.of(menuItem));

            Bill calculatedBill = new Bill();
            calculatedBill.setTotalAmount(new BigDecimal("10.00"));
            when(calculationService.calculateBill(any(RestaurantOrder.class))).thenReturn(calculatedBill);
            when(orderRepository.save(any(RestaurantOrder.class))).thenAnswer(invocation -> {
                RestaurantOrder order = invocation.getArgument(0);
                order.setId(11L);
                createdOrder = order;
                return order;
            });
            when(billService.getBillByOrderNumber("TX - #0000011")).thenReturn(new BillResponse());
        }

        private RestaurantOrder createOrder() {
            CreateOrderRequest orderRequest = new CreateOrderRequest();
            orderRequest.setQrToken(QR_TOKEN);
            OrderItemRequest item = new OrderItemRequest();
            item.setItemId(MENU_ITEM_ID);
            item.setQuantity(1);
            orderRequest.setItems(List.of(item));
            service.createOrder(orderRequest, request);
            return createdOrder;
        }
    }
}
