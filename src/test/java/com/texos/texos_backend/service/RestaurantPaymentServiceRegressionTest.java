package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.entity.CustomerPaymentTransaction;
import com.texos.texos_backend.entity.OfflineAssistanceRequest;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.OrderItem;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantPaymentNotification;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.ResourceConflictException;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.CustomerPaymentTransactionRepository;
import com.texos.texos_backend.repository.OfflineAssistanceRequestRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.PaymentAttemptRepository;
import com.texos.texos_backend.repository.PaymentAuditLogRepository;
import com.texos.texos_backend.repository.PaymentReceiptRepository;
import com.texos.texos_backend.repository.PhonePeWebhookEventRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantPaymentNotificationRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantPaymentServiceRegressionTest {
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private RestaurantOrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private BillRepository billRepository;
    @Mock private CustomerBillService customerBillService;
    @Mock private CustomerSessionService customerSessionService;
    @Mock private CustomerPaymentTransactionRepository transactionRepository;
    @Mock private PaymentAttemptRepository attemptRepository;
    @Mock private PaymentAuditLogRepository auditRepository;
    @Mock private RestaurantPhonePeConfigService configService;
    @Mock private PhonePeGateway phonePeGateway;
    @Mock private OfflineAssistanceRequestRepository assistanceRepository;
    @Mock private RestaurantPaymentNotificationRepository notificationRepository;
    @Mock private PhonePeWebhookEventRepository webhookRepository;
    @Mock private PaymentReceiptRepository receiptRepository;
    @Mock private OwnerRepository ownerRepository;
    @Mock private LoyaltyService loyaltyService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RestaurantPaymentService service;

    @Test
    void allowsWaiterRecallAfterCooldownAndCreatesAnotherOwnerNotification() {
        RestaurantTable table = activeTable();
        OfflineAssistanceRequest assistance = assistance(table, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3));
        when(tableRepository.findByQrTokenAndActiveTrue("qr")).thenReturn(Optional.of(table));
        when(assistanceRepository.findByIdForUpdate(19L)).thenReturn(Optional.of(assistance));
        when(customerSessionService.getOrderNumbers(any(), eq(4L))).thenReturn(Set.of("TX-#104"));

        service.recallWaiter(19L, "qr", org.mockito.Mockito.mock(HttpServletRequest.class));

        ArgumentCaptor<RestaurantPaymentNotification> notification =
                ArgumentCaptor.forClass(RestaurantPaymentNotification.class);
        verify(notificationRepository).save(notification.capture());
        assertEquals("CALL_WAITER", notification.getValue().getNotificationType());
        assertEquals("PENDING", notification.getValue().getNotificationStatus());
        assertEquals(assistance, notification.getValue().getAssistanceRequest());
    }

    @Test
    void rejectsWaiterRecallBeforeCooldownWithoutCreatingNotification() {
        RestaurantTable table = activeTable();
        OfflineAssistanceRequest assistance = assistance(table, LocalDateTime.now(ZoneOffset.UTC));
        when(tableRepository.findByQrTokenAndActiveTrue("qr")).thenReturn(Optional.of(table));
        when(assistanceRepository.findByIdForUpdate(19L)).thenReturn(Optional.of(assistance));
        when(customerSessionService.getOrderNumbers(any(), eq(4L))).thenReturn(Set.of("TX-#104"));

        ResourceConflictException error = assertThrows(
                ResourceConflictException.class,
                () -> service.recallWaiter(
                        19L, "qr", org.mockito.Mockito.mock(HttpServletRequest.class)));

        assertEquals("Please wait 2 minutes before trying again.", error.getMessage());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void recordsOfflinePaymentAndStoresReceiptDateAsIsoText() throws Exception {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(4L);
        restaurant.setRestaurantName("Test Restaurant");
        RestaurantTable table = new RestaurantTable();
        table.setId(8L);
        table.setTableNumber("8");
        table.setRestaurant(restaurant);
        RestaurantOrder order = new RestaurantOrder();
        order.setId(12L);
        order.setOrderNumber("TX-#104");
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setOrderStatus(OrderStatus.NEW);
        order.setApprovalStatus(OrderApprovalStatus.NOT_REQUIRED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setTotalAmount(new BigDecimal("25.00"));
        order.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setItemName("Tea");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setLineTotal(new BigDecimal("25.00"));
        order.setItems(List.of(item));
        BillResponse bill = new BillResponse();
        bill.setOrderIds(List.of(12L));

        when(restaurantRepository.findByIdAndOwnerId(4L, 6L)).thenReturn(Optional.of(restaurant));
        when(customerBillService.getBillForOwner(4L, 12L)).thenReturn(bill);
        when(orderRepository.findByIdAndRestaurantIdForUpdate(12L, 4L))
                .thenReturn(Optional.of(order));
        when(transactionRepository.findByRestaurantAndActiveKeyForUpdate(4L, "4:12"))
                .thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(CustomerPaymentTransaction.class)))
                .thenAnswer(invocation -> {
                    CustomerPaymentTransaction transaction = invocation.getArgument(0);
                    transaction.setId(31L);
                    return transaction;
                });
        when(receiptRepository.findByPaymentTransaction_Id(31L)).thenReturn(Optional.empty());
        when(billRepository.findByOrderOrderNumber("TX-#104")).thenReturn(Optional.empty());

        Map<String, Object> result = service.recordOfflinePayment(4L, 12L, 6L, "CASH");

        ArgumentCaptor<com.texos.texos_backend.entity.PaymentReceipt> receipt =
                ArgumentCaptor.forClass(com.texos.texos_backend.entity.PaymentReceipt.class);
        verify(receiptRepository).save(receipt.capture());
        String receiptJson = receipt.getValue().getReceiptJson();
        assertTrue(receiptJson.contains("\"paymentDate\":\""));
        assertTrue(receiptJson.contains("Z\""));
        assertEquals(31L, result.get("transactionId"));
    }

    private RestaurantTable activeTable() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(4L);
        restaurant.setActive(true);
        RestaurantTable table = new RestaurantTable();
        table.setId(8L);
        table.setRestaurant(restaurant);
        return table;
    }

    private OfflineAssistanceRequest assistance(
            RestaurantTable table, LocalDateTime lastRecalledAt) {
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderNumber("TX-#104");
        order.setOrderSessionKey(null);
        OfflineAssistanceRequest assistance = new OfflineAssistanceRequest();
        assistance.setId(19L);
        assistance.setRestaurant(table.getRestaurant());
        assistance.setTable(table);
        assistance.setLatestOrder(order);
        assistance.setRequestedAt(lastRecalledAt);
        assistance.setLastRecalledAt(lastRecalledAt);
        assistance.setRequestStatus("PENDING");
        return assistance;
    }
}
