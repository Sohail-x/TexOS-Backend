package com.texos.texos_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.config.AuthenticatedOwnerPrincipal;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.RestaurantPaymentService;
import com.texos.texos_backend.service.RestaurantPhonePeConfigService;
import com.texos.texos_backend.service.RestaurantService;
import com.texos.texos_backend.service.SubscriptionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AuthenticatedOwnerControllerTest {

    @Test
    void paymentEndpointReusesVerifiedRequestOwnerAndKeepsAuthenticationName() {
        OwnerRepository owners = mock(OwnerRepository.class);
        RestaurantPaymentService payments = mock(RestaurantPaymentService.class);
        var controller = new RestaurantPaymentController(payments, owners);
        var authentication = ownerAuthentication("owner@example.com");
        MockHttpServletRequest request = requestWithOwner(7L);
        when(payments.getNextOwnerNotification(42L, 7L)).thenReturn(Map.of());

        Map<String, Object> response =
                controller.nextNotification(42L, authentication, request);

        assertEquals(Map.of(), response);
        assertEquals("owner@example.com", authentication.getName());
        verify(payments).getNextOwnerNotification(42L, 7L);
        verifyNoInteractions(owners);
    }

    @Test
    void subscriptionStatusReusesVerifiedRequestOwner() {
        OwnerRepository owners = mock(OwnerRepository.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        var controller = new SubscriptionController(subscriptions, owners);
        MockHttpServletRequest request = requestWithOwner(7L);
        when(subscriptions.getStatus(42L, 7L)).thenReturn(Map.of("entitled", true));

        var response = controller.status(
                42L, ownerAuthentication("owner@example.com"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("entitled", true), response.getBody());
        verify(subscriptions).getStatus(42L, 7L);
        verifyNoInteractions(owners);
    }

    @Test
    void subscriptionStatusRetainsOwnerLookupFallbackWithoutRequestPrincipal() {
        OwnerRepository owners = mock(OwnerRepository.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        var controller = new SubscriptionController(subscriptions, owners);
        Owner owner = new Owner();
        owner.setId(8L);
        when(owners.findByEmail("legacy-owner@example.com")).thenReturn(Optional.of(owner));
        when(subscriptions.getStatus(42L, 8L)).thenReturn(Map.of("entitled", true));

        var response = controller.status(
                42L,
                ownerAuthentication("legacy-owner@example.com"),
                new MockHttpServletRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(owners).findByEmail("legacy-owner@example.com");
        verify(owners, never()).findByPhone("legacy-owner@example.com");
        verify(subscriptions).getStatus(42L, 8L);
    }

    @Test
    void restaurantOwnerRoutesReuseVerifiedRequestOwner() {
        OwnerRepository owners = mock(OwnerRepository.class);
        RestaurantService restaurants = mock(RestaurantService.class);
        var controller = new RestaurantController(restaurants, owners);
        when(restaurants.getRestaurantsByOwnerId(7L)).thenReturn(List.of());

        var response = controller.getRestaurantsByOwner(
                7L,
                ownerAuthentication("owner@example.com"),
                requestWithOwner(7L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody());
        verify(restaurants).getRestaurantsByOwnerId(7L);
        verifyNoInteractions(owners);
    }

    @Test
    void phonePeSettingsRouteReusesVerifiedRequestOwner() {
        OwnerRepository owners = mock(OwnerRepository.class);
        RestaurantPhonePeConfigService configService =
                mock(RestaurantPhonePeConfigService.class);
        var controller = new RestaurantPhonePeConfigController(configService, owners);
        when(configService.getSettings(42L, 7L)).thenReturn(Map.of("configured", false));

        var response = controller.getSettings(
                42L,
                ownerAuthentication("owner@example.com"),
                requestWithOwner(7L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("configured", false), response.getBody());
        verify(configService).getSettings(42L, 7L);
        verifyNoInteractions(owners);
    }

    @Test
    void paymentEndpointRetainsOwnerLookupFallbackWithoutRequestPrincipal() {
        OwnerRepository owners = mock(OwnerRepository.class);
        RestaurantPaymentService payments = mock(RestaurantPaymentService.class);
        var controller = new RestaurantPaymentController(payments, owners);
        Owner owner = new Owner();
        owner.setId(8L);
        when(owners.findByEmail("legacy-owner@example.com")).thenReturn(Optional.of(owner));
        when(payments.getNextOwnerNotification(42L, 8L)).thenReturn(Map.of());

        controller.nextNotification(
                42L,
                ownerAuthentication("legacy-owner@example.com"),
                new MockHttpServletRequest());

        verify(owners).findByEmail("legacy-owner@example.com");
        verify(owners, never()).findByPhone("legacy-owner@example.com");
        verify(payments).getNextOwnerNotification(42L, 8L);
    }

    private static MockHttpServletRequest requestWithOwner(Long ownerId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE,
                new AuthenticatedOwnerPrincipal(ownerId));
        return request;
    }

    private static UsernamePasswordAuthenticationToken ownerAuthentication(String name) {
        return new UsernamePasswordAuthenticationToken(
                name, null, List.of(() -> "ROLE_OWNER"));
    }
}
