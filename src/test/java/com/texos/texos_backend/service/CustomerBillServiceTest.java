package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerBillServiceTest {
    private static final long RESTAURANT_ID = 2L;
    private static final long CUSTOMER_ID = 7L;
    private static final long TABLE_ID = 9L;
    private static final String QR_TOKEN = "table-qr";

    @Test
    void combinesRecentOrdersForTheSameCustomerAndTable() {
        TestContext context = new TestContext();
        LocalDateTime now = LocalDateTime.now();
        RestaurantOrder latest = order("latest", context.table, now.minusMinutes(15));
        RestaurantOrder earlier = order("earlier", context.table, now.minusMinutes(90));
        context.stubOrders(List.of(latest, earlier));
        when(context.billService.getBillsForOrders(List.of(earlier, latest)))
                .thenReturn(List.of(bill("BILL-1", "40.00"), bill("BILL-2", "60.00")));

        BillResponse result = context.service.getCurrentBill(QR_TOKEN, mock(HttpServletRequest.class));

        assertEquals("BILL-1", result.getBillNumber());
        assertEquals("latest", result.getOrderNumber());
        assertEquals(new BigDecimal("100.00"), result.getTotalAmount());
        assertNull(result.getExpiresAt());
        verify(context.billService).getBillsForOrders(List.of(earlier, latest));
    }

    @Test
    void doesNotIncludeOrdersFromPreviousPaidSittings() {
        TestContext context = new TestContext();
        LocalDateTime now = LocalDateTime.now();
        RestaurantOrder latest = order("latest", context.table, now.minusMinutes(15));
        latest.setOrderSessionKey("current-sitting");
        RestaurantOrder earlier = order("earlier", context.table, now.minusMinutes(45));
        earlier.setOrderSessionKey("current-sitting");
        RestaurantOrder previousSitting = order("previous", context.table, now.minusHours(5));
        previousSitting.setOrderSessionKey("previous-sitting");
        previousSitting.setPaymentStatus(PaymentStatus.PAID);
        context.stubOrders(List.of(latest, earlier, previousSitting));
        when(context.customerSessionService.getOrderSessionKey(any(HttpServletRequest.class), eq(RESTAURANT_ID), eq(TABLE_ID)))
                .thenReturn("current-sitting");
        when(context.billService.getBillsForOrders(List.of(earlier, latest)))
                .thenReturn(List.of(bill("BILL-2", "60.00"), bill("BILL-1", "40.00")));

        BillResponse result = context.service.getCurrentBill(QR_TOKEN, mock(HttpServletRequest.class));

        assertEquals(new BigDecimal("100.00"), result.getTotalAmount());
        verify(context.billService).getBillsForOrders(List.of(earlier, latest));
        verify(context.billService, never()).getBillByOrderNumber("previous");
    }

    @Test
    void keepsAnUnpaidBillAvailableAfterTwoHours() {
        TestContext context = new TestContext();
        RestaurantOrder order = order("open", context.table, LocalDateTime.now().minusHours(5));
        order.setOrderSessionKey("open-sitting");
        context.stubOrders(List.of(order));
        when(context.customerSessionService.getOrderSessionKey(any(HttpServletRequest.class), eq(RESTAURANT_ID), eq(TABLE_ID)))
                .thenReturn("open-sitting");
        when(context.billService.getBillsForOrders(List.of(order)))
                .thenReturn(List.of(bill("BILL-1", "40.00")));

        BillResponse result = context.service.getCurrentBill(QR_TOKEN, mock(HttpServletRequest.class));

        assertEquals(new BigDecimal("40.00"), result.getTotalAmount());
        assertNull(result.getExpiresAt());
    }

    private static RestaurantOrder order(String number, RestaurantTable table, LocalDateTime createdAt) {
        RestaurantOrder order = new RestaurantOrder();
        order.setId((long) number.hashCode());
        order.setOrderNumber(number);
        order.setTable(table);
        order.setRestaurant(table.getRestaurant());
        order.setCreatedAt(createdAt);
        order.setOrderStatus(OrderStatus.NEW);
        order.setPaymentStatus(PaymentStatus.PENDING);
        return order;
    }

    private static BillResponse bill(String number, String total) {
        BillResponse bill = new BillResponse();
        bill.setBillNumber(number);
        bill.setSubtotal(new BigDecimal(total));
        bill.setTaxAmount(BigDecimal.ZERO);
        bill.setServiceChargeAmount(BigDecimal.ZERO);
        bill.setRewardDiscountAmount(BigDecimal.ZERO);
        bill.setTotalAmount(new BigDecimal(total));
        bill.setPaymentStatus(PaymentStatus.PENDING);
        return bill;
    }

    private static class TestContext {
        private final Restaurant restaurant = new Restaurant();
        private final RestaurantTable table = new RestaurantTable();
        private final RestaurantTableRepository tableRepository = mock(RestaurantTableRepository.class);
        private final RestaurantOrderRepository orderRepository = mock(RestaurantOrderRepository.class);
        private final CustomerSessionService customerSessionService = mock(CustomerSessionService.class);
        private final BillService billService = mock(BillService.class);
        private final RestaurantPhonePeConfigService phonePeConfigService = mock(RestaurantPhonePeConfigService.class);
        private final CustomerBillService service = new CustomerBillService(
                tableRepository, orderRepository, customerSessionService, billService, phonePeConfigService);

        private TestContext() {
            restaurant.setId(RESTAURANT_ID);
            restaurant.setRestaurantName("Test restaurant");
            restaurant.setActive(true);
            table.setId(TABLE_ID);
            table.setTableNumber("T1");
            table.setRestaurant(restaurant);
            when(tableRepository.findByQrTokenAndActiveTrue(QR_TOKEN)).thenReturn(Optional.of(table));
            when(customerSessionService.getActiveSession(any(HttpServletRequest.class), eq(RESTAURANT_ID)))
                    .thenReturn(new CustomerSession(CUSTOMER_ID, RESTAURANT_ID));
        }

        private void stubOrders(List<RestaurantOrder> orders) {
            when(orderRepository.findAllByCustomer_IdAndRestaurant_IdAndTable_IdOrderByCreatedAtDesc(
                    CUSTOMER_ID, RESTAURANT_ID, TABLE_ID)).thenReturn(orders);
        }
    }
}
