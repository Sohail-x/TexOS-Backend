package com.texos.texos_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RestaurantAccessFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsOwnerToAccessOwnRestaurant() throws Exception {
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        Owner owner = owner(7L);
        when(ownerRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(owner));
        when(restaurantRepository.findByIdAndOwnerId(42L, 7L))
                .thenReturn(Optional.of(new Restaurant()));
        when(subscriptionService.hasValidSubscription(42L)).thenReturn(true);
        authenticate();

        MockHttpServletRequest request = request(42L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new RestaurantAccessFilter(ownerRepository, restaurantRepository, subscriptionService)
                .doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void reusesVerifiedOwnerForOwnerRestaurantListRoute() throws Exception {
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(subscriptionService.hasAnyValidSubscriptionForOwner(7L)).thenReturn(true);
        authenticate();

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/restaurants/owner/7/orders");
        request.setAttribute(
                AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE,
                new AuthenticatedOwnerPrincipal(7L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new RestaurantAccessFilter(ownerRepository, restaurantRepository, subscriptionService)
                .doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        verifyNoInteractions(ownerRepository, restaurantRepository);
        verify(subscriptionService).hasAnyValidSubscriptionForOwner(7L);
    }

    @Test
    void deniesOwnerAccessToAnotherRestaurantsCustomers() throws Exception {
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(ownerRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(owner(7L)));
        when(restaurantRepository.findByIdAndOwnerId(84L, 7L))
                .thenReturn(Optional.empty());
        authenticate();

        MockHttpServletRequest request = request(84L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new RestaurantAccessFilter(ownerRepository, restaurantRepository, subscriptionService)
                .doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void deniesRestaurantAccessWithoutSubscription() throws Exception {
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(ownerRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(owner(7L)));
        when(restaurantRepository.findByIdAndOwnerId(42L, 7L))
                .thenReturn(Optional.of(new Restaurant()));
        when(subscriptionService.hasValidSubscription(42L)).thenReturn(false);
        authenticate();

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new RestaurantAccessFilter(ownerRepository, restaurantRepository, subscriptionService)
                .doFilter(request(42L), response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertNull(chain.getRequest());
    }

    @Test
    void missingJwtGetsJsonUnauthorized() throws Exception {
        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new RestaurantAccessFilter(ownerRepository, restaurantRepository, subscriptionService)
                .doFilter(request(42L), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertNull(chain.getRequest());
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "owner@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private MockHttpServletRequest request(Long restaurantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/restaurants/" + restaurantId + "/customers");
        request.setMethod("GET");
        return request;
    }

    private Owner owner(Long ownerId) {
        Owner owner = new Owner();
        owner.setId(ownerId);
        owner.setActive(true);
        return owner;
    }
}
