package com.texos.texos_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import com.texos.texos_backend.service.CustomerAuthService;
import com.texos.texos_backend.service.CustomerSession;
import com.texos.texos_backend.service.CustomerSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CustomerAuthControllerTest {
    @Test
    void customerSessionReturnsNameIndependentlyOfRewardStatus() {
        long restaurantId = 7L;
        long customerId = 12L;
        String qrToken = "table-token";

        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setActive(true);
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setCustomerName("Asha");

        RestaurantTableRepository tableRepository = mock(RestaurantTableRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CustomerSessionService customerSessionService = mock(CustomerSessionService.class);
        when(tableRepository.findByQrTokenAndActiveTrue(qrToken))
                .thenReturn(Optional.of(table));
        when(customerSessionService.getActiveSession(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                org.mockito.ArgumentMatchers.eq(restaurantId)))
                .thenReturn(new CustomerSession(customerId, restaurantId));
        when(customerRepository.findByIdAndRestaurant_Id(customerId, restaurantId))
                .thenReturn(Optional.of(customer));

        CustomerAuthController controller = new CustomerAuthController(
                mock(CustomerAuthService.class),
                customerSessionService,
                tableRepository,
                customerRepository);

        ResponseEntity<?> response = controller.checkSession(
                qrToken, mock(HttpServletRequest.class));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Asha", ((Map<?, ?>) response.getBody()).get("customerName"));
    }
}
