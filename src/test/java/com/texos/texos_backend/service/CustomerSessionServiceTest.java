package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.texos.texos_backend.repository.CustomerRepository;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerSessionServiceTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerSessionService customerSessionService =
            new CustomerSessionService(customerRepository);

    @Test
    void guestOrderAccessIsScopedToBrowserSessionAndRestaurant() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setNew(false);
        request.setSession(session);

        customerSessionService.registerOrderAccess(request, 11L, "order-a1");
        customerSessionService.registerOrderAccess(request, 11L, "order-a2");
        customerSessionService.registerOrderAccess(request, 22L, "order-b1");

        assertEquals(
                Set.of("order-a1", "order-a2"),
                customerSessionService.getOrderNumbers(request, 11L));
        assertEquals(
                Set.of("order-b1"),
                customerSessionService.getOrderNumbers(request, 22L));
        assertEquals(
                Set.of(),
                customerSessionService.getOrderNumbers(
                        new MockHttpServletRequest(), 11L));
    }

    @Test
    void deletedCustomerSessionIsRejectedAndRemoved() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        customerSessionService.createSession(request, 27L, 11L);
        when(customerRepository.existsByIdAndRestaurant_IdAndActiveTrueAndDeletedAtIsNull(
                27L, 11L))
                .thenReturn(false);

        assertNull(customerSessionService.getActiveSession(request, 11L));
    }
}
