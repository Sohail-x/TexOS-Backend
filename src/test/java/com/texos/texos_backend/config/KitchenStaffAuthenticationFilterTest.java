package com.texos.texos_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.service.KitchenAccessService;
import com.texos.texos_backend.service.KitchenStaffPrincipal;
import com.texos.texos_backend.service.JwtService;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class KitchenStaffAuthenticationFilterTest {

    private final JwtService jwtService =
            new JwtService("test-secret-that-is-at-least-32-bytes-long");
    private final KitchenAccessService accessService = mock(KitchenAccessService.class);
    private final KitchenStaffAuthenticationFilter filter =
            new KitchenStaffAuthenticationFilter(jwtService, accessService);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyAnActiveRestaurantBoundStaffSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateKitchenStaffToken(
                sessionId, 42L, new Date(System.currentTimeMillis() + 60_000));
        when(accessService.findActiveSession(sessionId, 42L))
                .thenReturn(new com.texos.texos_backend.entity.KitchenAccessGrant());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(token), new MockHttpServletResponse(), chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("ROLE_KITCHEN_STAFF", authentication.getAuthorities().iterator().next().getAuthority());
        assertEquals(
                new KitchenStaffPrincipal(42L, sessionId),
                authentication.getPrincipal());
        verify(accessService).findActiveSession(sessionId, 42L);
    }

    @Test
    void doesNotAuthenticateRevokedSessionsOrOwnerTokens() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String staffToken = jwtService.generateKitchenStaffToken(
                sessionId, 42L, new Date(System.currentTimeMillis() + 60_000));
        when(accessService.findActiveSession(sessionId, 42L)).thenReturn(null);

        filter.doFilter(request(staffToken), new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        SecurityContextHolder.clearContext();
        String ownerToken = jwtService.generateToken("owner@example.com", 0);
        filter.doFilter(request(ownerToken), new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/kitchen-staff/orders");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
