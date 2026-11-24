package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.repository.CustomerPaymentTransactionRepository;
import com.texos.texos_backend.repository.OfflineAssistanceRequestRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantTableServiceTest {
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private RestaurantOrderRepository orderRepository;
    @Mock private CustomerPaymentTransactionRepository paymentTransactionRepository;
    @Mock private OfflineAssistanceRequestRepository assistanceRequestRepository;

    @InjectMocks
    private RestaurantTableService service;

    @Test
    void deletesTableWhenItHasNoHistoricalReferences() {
        RestaurantTable table = stubTable();
        when(orderRepository.existsByTable_Id(7L)).thenReturn(false);
        when(paymentTransactionRepository.existsByTable_Id(7L)).thenReturn(false);
        when(assistanceRequestRepository.existsByTable_Id(7L)).thenReturn(false);

        service.deleteTable(3L, 7L);

        verify(tableRepository).delete(table);
    }

    @Test
    void refusesToDeleteTableWithOrderHistory() {
        stubTable();
        when(orderRepository.existsByTable_Id(7L)).thenReturn(true);

        InvalidRequestException error = assertThrows(
                InvalidRequestException.class,
                () -> service.deleteTable(3L, 7L));

        assertEquals(
                "This table has existing orders, payments, or service requests and cannot be deleted. Deactivate it instead.",
                error.getMessage());
        verify(tableRepository, never()).delete(any(RestaurantTable.class));
    }

    @Test
    void refusesToDeleteTableWithPaymentHistory() {
        stubTable();
        when(orderRepository.existsByTable_Id(7L)).thenReturn(false);
        when(paymentTransactionRepository.existsByTable_Id(7L)).thenReturn(true);

        assertThrows(InvalidRequestException.class, () -> service.deleteTable(3L, 7L));

        verify(tableRepository, never()).delete(any(RestaurantTable.class));
    }

    @Test
    void refusesToDeleteTableWithServiceRequestHistory() {
        stubTable();
        when(orderRepository.existsByTable_Id(7L)).thenReturn(false);
        when(paymentTransactionRepository.existsByTable_Id(7L)).thenReturn(false);
        when(assistanceRequestRepository.existsByTable_Id(7L)).thenReturn(true);

        assertThrows(InvalidRequestException.class, () -> service.deleteTable(3L, 7L));

        verify(tableRepository, never()).delete(any(RestaurantTable.class));
    }

    private RestaurantTable stubTable() {
        RestaurantTable table = new RestaurantTable();
        table.setId(7L);
        when(tableRepository.findByIdAndRestaurantId(7L, 3L)).thenReturn(Optional.of(table));
        return table;
    }
}
