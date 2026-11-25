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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BillServiceTest {

    @Test
    void assignsInvoiceNumberFromPersistedBillId() {
        BillRepository repository = mock(BillRepository.class);
        OrderCalculationService calculation = mock(OrderCalculationService.class);
        BillService service = new BillService(repository, calculation);
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
        BillService service = new BillService(repository, mock(OrderCalculationService.class));
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
        BillService service = new BillService(repository, mock(OrderCalculationService.class));
        RestaurantOrder order = order(1L, "missing", new Restaurant(), new RestaurantTable());
        when(repository.findAllWithOrderDetailsByOrder_IdIn(List.of(1L)))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.getBillsForOrders(List.of(order)));
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
