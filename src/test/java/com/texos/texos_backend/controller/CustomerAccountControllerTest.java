package com.texos.texos_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.AccountDeletionRequest;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import com.texos.texos_backend.service.CustomerAuthService;
import com.texos.texos_backend.service.CustomerSession;
import com.texos.texos_backend.service.CustomerSessionService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CustomerAccountControllerTest {

    @Test
    void deletesOnlyTheQrRestaurantCustomerAndExpiresTheSessionCookie() {
        long restaurantId = 7L;
        long customerId = 12L;
        String qrToken = "table-token";
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        RestaurantTableRepository tableRepository = mock(RestaurantTableRepository.class);
        CustomerSessionService sessions = mock(CustomerSessionService.class);
        CustomerAuthService customerAuthService = mock(CustomerAuthService.class);
        when(tableRepository.findByQrTokenAndActiveTrue(qrToken))
                .thenReturn(Optional.of(table));
        when(sessions.getActiveSession(any(), org.mockito.ArgumentMatchers.eq(restaurantId)))
                .thenReturn(new CustomerSession(customerId, restaurantId));

        CustomerAccountController controller = new CustomerAccountController(
                customerAuthService, sessions, tableRepository, "None");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.deleteAccount(
                qrToken, new AccountDeletionRequest("DELETE"), request, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("Customer account deactivated. Profile anonymization is scheduled within 30 days.",
                result.getBody().get("message"));
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        verify(customerAuthService).deleteAccount(customerId, restaurantId);
        verify(sessions).logout(request, restaurantId);
    }
}
