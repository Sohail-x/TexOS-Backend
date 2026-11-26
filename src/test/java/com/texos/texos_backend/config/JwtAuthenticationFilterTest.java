package com.texos.texos_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.OwnerRepository.OwnerAuthView;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.service.AdminCredentials;
import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.service.SubscriptionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String JWT_SECRET = "test-secret-that-is-at-least-32-bytes-long";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerPrincipalIsRequestScopedAndReusedByRestaurantAccessFilter() throws Exception {
        OwnerAuthView owner = owner(7L, "owner@example.com", "5550001", true, 3);
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        AdminCredentials adminCredentials = mock(AdminCredentials.class);
        JwtService jwtService = spy(new JwtService(JWT_SECRET));
        String token = jwtService.generateToken(owner.getEmail(), owner.getTokenVersion());
        when(ownerRepository.findAuthViewByIdentity(owner.getEmail())).thenReturn(Optional.of(owner));
        when(restaurantRepository.existsByIdAndOwnerId(42L, owner.getId())).thenReturn(true);
        when(subscriptionService.hasValidSubscription(42L)).thenReturn(true);

        MockHttpServletRequest request = dashboardRequest(42L, token);
        new JwtAuthenticationFilter(jwtService, ownerRepository, adminCredentials)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(owner.getEmail(), authentication.getName());
        assertEquals(List.of("ROLE_OWNER"),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority()).toList());
        assertInstanceOf(String.class, authentication.getPrincipal());
        AuthenticatedOwnerPrincipal principal = assertInstanceOf(
                AuthenticatedOwnerPrincipal.class,
                request.getAttribute(AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE));
        assertEquals(owner.getId(), principal.ownerId());

        MockHttpServletResponse accessResponse = new MockHttpServletResponse();
        new RestaurantAccessFilter(ownerRepository, restaurantRepository, subscriptionService)
                .doFilter(request, accessResponse, new MockFilterChain());

        assertEquals(200, accessResponse.getStatus());
        verify(ownerRepository, times(1)).findAuthViewByIdentity(owner.getEmail());
        verify(ownerRepository, never()).findByEmail(anyString());
        verify(ownerRepository, never()).findByPhone(anyString());
        verify(restaurantRepository).existsByIdAndOwnerId(42L, owner.getId());
        verify(subscriptionService).hasValidSubscription(42L);
        verify(jwtService, times(1)).parseAuthenticationToken(token);
        verify(jwtService, never()).extractEmail(token);
        verify(jwtService, never()).extractRole(token);
        verify(jwtService, never()).isTokenValid(eq(token), anyString(), anyInt());
    }

    @Test
    void phoneOnlyOwnerLookupIsNotRepeatedByRestaurantAccessFilter() throws Exception {
        OwnerAuthView owner = owner(8L, null, "5550002", true, 1);
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        JwtService jwtService = spy(new JwtService(JWT_SECRET));
        String token = jwtService.generateToken(owner.getPhone(), owner.getTokenVersion());
        when(ownerRepository.findAuthViewByIdentity(owner.getPhone())).thenReturn(Optional.of(owner));
        when(restaurantRepository.existsByIdAndOwnerId(42L, owner.getId())).thenReturn(true);
        when(subscriptionService.hasValidSubscription(42L)).thenReturn(true);

        MockHttpServletRequest request = dashboardRequest(42L, token);
        new JwtAuthenticationFilter(jwtService, ownerRepository, mock(AdminCredentials.class))
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        new RestaurantAccessFilter(ownerRepository, restaurantRepository, subscriptionService)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(ownerRepository, times(1)).findAuthViewByIdentity(owner.getPhone());
        verify(ownerRepository, never()).findByEmail(anyString());
        verify(ownerRepository, never()).findByPhone(anyString());
        verify(restaurantRepository).existsByIdAndOwnerId(42L, owner.getId());
        verify(jwtService, times(1)).parseAuthenticationToken(token);
    }

    @Test
    void inactiveOwnerIsNotAuthenticatedOrGivenRequestPrincipal() throws Exception {
        OwnerAuthView owner = owner(9L, "inactive@example.com", null, false, 2);
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        JwtService jwtService = spy(new JwtService(JWT_SECRET));
        String token = jwtService.generateToken(owner.getEmail(), owner.getTokenVersion());
        when(ownerRepository.findAuthViewByIdentity(owner.getEmail())).thenReturn(Optional.of(owner));
        MockHttpServletRequest request = dashboardRequest(42L, token);

        new JwtAuthenticationFilter(jwtService, ownerRepository, mock(AdminCredentials.class))
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute(AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE));
        verify(jwtService, never()).isTokenValid(
                org.mockito.ArgumentMatchers.any(JwtService.VerifiedTokenClaims.class),
                anyString(),
                anyInt());
    }

    @Test
    void staleTokenVersionIsNotAuthenticatedOrGivenRequestPrincipal() throws Exception {
        OwnerAuthView owner = owner(10L, "versioned@example.com", null, true, 5);
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        JwtService jwtService = spy(new JwtService(JWT_SECRET));
        String token = jwtService.generateToken(owner.getEmail(), 4);
        when(ownerRepository.findAuthViewByIdentity(owner.getEmail())).thenReturn(Optional.of(owner));
        MockHttpServletRequest request = dashboardRequest(42L, token);

        new JwtAuthenticationFilter(jwtService, ownerRepository, mock(AdminCredentials.class))
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute(AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE));
        verify(jwtService).parseAuthenticationToken(token);
    }

    @Test
    void adminAuthenticationIsPreservedAndDoesNotLoadAnOwner() throws Exception {
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        AdminCredentials adminCredentials = mock(AdminCredentials.class);
        JwtService jwtService = spy(new JwtService(JWT_SECRET));
        String token = jwtService.generateAdminToken("texos-admin");
        when(adminCredentials.isUsername("texos-admin")).thenReturn(true);
        MockHttpServletRequest request = dashboardRequest(42L, token);

        new JwtAuthenticationFilter(jwtService, ownerRepository, adminCredentials)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("texos-admin", authentication.getName());
        assertInstanceOf(String.class, authentication.getPrincipal());
        assertEquals(List.of("ROLE_TEXOS_ADMIN"),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority()).toList());
        assertNull(request.getAttribute(AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE));
        verify(jwtService, times(1)).parseAuthenticationToken(token);
        verifyNoInteractions(ownerRepository);
    }

    private static MockHttpServletRequest dashboardRequest(Long restaurantId, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/restaurants/" + restaurantId + "/dashboard/summary");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static OwnerAuthView owner(
            Long id,
            String email,
            String phone,
            boolean active,
            int tokenVersion) {
        return new OwnerAuthView() {
            @Override public Long getId() { return id; }
            @Override public String getEmail() { return email; }
            @Override public String getPhone() { return phone; }
            @Override public boolean isActive() { return active; }
            @Override public int getTokenVersion() { return tokenVersion; }
        };
    }
}
