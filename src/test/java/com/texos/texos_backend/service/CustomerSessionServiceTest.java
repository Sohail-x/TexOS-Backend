package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class CustomerSessionServiceTest {

    private final CustomerSessionService customerSessionService =
            new CustomerSessionService();

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
}
