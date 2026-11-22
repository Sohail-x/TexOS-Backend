package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.repository.BillRepository;
import java.math.BigDecimal;
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
}
