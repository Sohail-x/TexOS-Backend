package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.entity.OrderItem;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BillServiceTest {

    @Test
    void assignsInvoiceNumberFromPersistedBillId() {
        BillRepository repository = mock(BillRepository.class);
        OrderCalculationService calculation = mock(OrderCalculationService.class);
        BillService service = new BillService(
                repository, calculation, mock(RestaurantOrderRepository.class));
        RestaurantOrder order = new RestaurantOrder();
        Bill bill = new Bill();
        bill.setTotalAmount(new BigDecimal("25.00"));

        when(calculation.calculateBill(order)).thenReturn(bill);
        when(repository.saveAndFlush(any(Bill.class))).thenAnswer(invocation -> {
            Bill saved = invocation.getArgument(0);
            saved.setId(123L);
            return saved;
        });
        when(repository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Bill result = service.createBill(order);

        assertEquals("INV/123", result.getBillNumber());
    }

    @Test
    void bulkBillMappingPreservesRequestedOrderAndOrderItemDetails() {
        BillRepository repository = mock(BillRepository.class);
        BillService service = new BillService(
                repository, mock(OrderCalculationService.class), mock(RestaurantOrderRepository.class));
        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName("Test restaurant");
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T1");
        RestaurantOrder firstOrder = order(1L, "first", restaurant, table);
        RestaurantOrder secondOrder = order(2L, "second", restaurant, table);
        Bill firstBill = bill("BILL-1", firstOrder);
        Bill secondBill = bill("BILL-2", secondOrder);
        when(repository.findAllWithOrderDetailsByOrder_IdIn(List.of(1L, 2L)))
                .thenReturn(List.of(secondBill, firstBill));

        List<BillResponse> result = service.getBillsForOrders(List.of(firstOrder, secondOrder));

        assertEquals(List.of("first", "second"),
                result.stream().map(BillResponse::getOrderNumber).toList());
        assertEquals("Soup", result.get(0).getItems().get(0).getItemName());
        verify(repository).findAllWithOrderDetailsByOrder_IdIn(eq(List.of(1L, 2L)));
    }

    @Test
    void bulkBillMappingKeepsMissingBillAsNotFound() {
        BillRepository repository = mock(BillRepository.class);
        BillService service = new BillService(
                repository, mock(OrderCalculationService.class), mock(RestaurantOrderRepository.class));
        RestaurantOrder order = order(1L, "missing", new Restaurant(), new RestaurantTable());
        when(repository.findAllWithOrderDetailsByOrder_IdIn(List.of(1L)))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.getBillsForOrders(List.of(order)));
    }

    @Test
    void addingAnOrderToAnUnpaidSittingExtendsItsExistingBill() {
        BillRepository repository = mock(BillRepository.class);
        OrderCalculationService calculation = mock(OrderCalculationService.class);
        RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
        BillService service = new BillService(repository, calculation, orders);
        RestaurantOrder secondOrder = sessionOrder(2L, "second", "unpaid-session");
        Bill sittingBill = bill("INV/1", sessionOrder(1L, "first", "unpaid-session"));
        sittingBill.setOrderSessionKey("unpaid-session");
        sittingBill.setSubtotal(new BigDecimal("10.00"));
        sittingBill.setTaxAmount(new BigDecimal("1.00"));
        sittingBill.setTotalAmount(new BigDecimal("11.00"));
        sittingBill.setBillVersion(1);
        Bill additionalAmount = bill("temporary", secondOrder);
        additionalAmount.setSubtotal(new BigDecimal("5.00"));
        additionalAmount.setTaxAmount(new BigDecimal("0.50"));
        additionalAmount.setTotalAmount(new BigDecimal("5.50"));
        when(calculation.calculateBill(secondOrder)).thenReturn(additionalAmount);
        when(repository.findByOrderSessionKeyAndSupersededByBillIdIsNull("unpaid-session"))
                .thenReturn(java.util.Optional.of(sittingBill));
        when(repository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Bill result = service.createBill(secondOrder);

        assertEquals("INV/1", result.getBillNumber());
        assertEquals(new BigDecimal("15.00"), result.getSubtotal());
        assertEquals(new BigDecimal("1.50"), result.getTaxAmount());
        assertEquals(new BigDecimal("16.50"), result.getTotalAmount());
        assertEquals(2, result.getBillVersion());
        verify(repository).save(sittingBill);
    }

    @Test
    void aRotatedSessionAfterPaymentCreatesANewBill() {
        BillRepository repository = mock(BillRepository.class);
        OrderCalculationService calculation = mock(OrderCalculationService.class);
        BillService service = new BillService(
                repository, calculation, mock(RestaurantOrderRepository.class));
        RestaurantOrder nextOrder = sessionOrder(2L, "next", "after-payment-session");
        Bill calculated = bill("temporary", nextOrder);
        calculated.setSubtotal(BigDecimal.TEN);
        calculated.setTaxAmount(BigDecimal.ZERO);
        calculated.setTotalAmount(BigDecimal.TEN);
        when(calculation.calculateBill(nextOrder)).thenReturn(calculated);
        when(repository.findByOrderSessionKeyAndSupersededByBillIdIsNull("after-payment-session"))
                .thenReturn(java.util.Optional.empty());
        when(repository.saveAndFlush(any(Bill.class))).thenAnswer(invocation -> {
            Bill saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        when(repository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Bill result = service.createBill(nextOrder);

        assertEquals("INV/2", result.getBillNumber());
        assertEquals("after-payment-session", result.getOrderSessionKey());
    }

    @Test
    void bulkMappingReturnsOneAggregateBillForEveryOrderInTheSitting() {
        BillRepository repository = mock(BillRepository.class);
        RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
        BillService service = new BillService(
                repository, mock(OrderCalculationService.class), orders);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(9L);
        restaurant.setRestaurantName("Test restaurant");
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T1");
        RestaurantOrder first = sessionOrder(1L, "first", "same-session");
        first.setRestaurant(restaurant);
        first.setTable(table);
        RestaurantOrder second = sessionOrder(2L, "second", "same-session");
        second.setRestaurant(restaurant);
        second.setTable(table);
        Bill canonical = bill("INV/1", first);
        canonical.setOrderSessionKey("same-session");
        when(repository.findAllByOrderSessionKeyInAndSupersededByBillIdIsNull(
                List.of("same-session"))).thenReturn(List.of(canonical));
        when(orders.findAllByRestaurant_IdAndOrderSessionKeyInOrderByCreatedAtAsc(
                9L, List.of("same-session"))).thenReturn(List.of(first, second));

        List<BillResponse> result = service.getBillsForOrders(List.of(first, second));

        assertEquals(1, result.size());
        assertEquals("INV/1", result.get(0).getBillNumber());
        assertEquals(List.of(1L, 2L), result.get(0).getOrderIds());
        assertEquals(List.of("first", "second"), result.get(0).getOrderNumbers());
    }

    private static RestaurantOrder order(
            Long id, String number, Restaurant restaurant, RestaurantTable table) {
        RestaurantOrder order = new RestaurantOrder();
        order.setId(id);
        order.setOrderNumber(number);
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setOrderStatus(OrderStatus.NEW);
        order.setCreatedAt(LocalDateTime.of(2026, 11, 24, 10, 0).plusMinutes(id));
        OrderItem item = new OrderItem();
        item.setItemName("Soup");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.TEN);
        item.setLineTotal(BigDecimal.TEN);
        MenuItem menuItem = new MenuItem();
        menuItem.setId(5L);
        item.setMenuItem(menuItem);
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private static RestaurantOrder sessionOrder(Long id, String number, String sessionKey) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(9L);
        RestaurantTable table = new RestaurantTable();
        table.setId(3L);
        RestaurantOrder order = order(id, number, restaurant, table);
        order.setOrderSessionKey(sessionKey);
        return order;
    }

    private static Bill bill(String number, RestaurantOrder order) {
        Bill bill = new Bill();
        bill.setBillNumber(number);
        bill.setBillVersion(1);
        bill.setOrder(order);
        bill.setSubtotal(BigDecimal.TEN);
        bill.setTaxAmount(BigDecimal.ZERO);
        bill.setServiceChargeAmount(BigDecimal.ZERO);
        bill.setRewardDiscountAmount(BigDecimal.ZERO);
        bill.setTotalAmount(BigDecimal.TEN);
        return bill;
    }
}
