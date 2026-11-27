package com.texos.texos_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.config.AuthenticatedOwnerPrincipal;
import com.texos.texos_backend.dto.LoginRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.service.GoogleIdentityService;
import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.service.PasswordResetService;
import com.texos.texos_backend.service.RestaurantService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthControllerTest {

    @Test
    void meUsesJwtVerifiedOwnerSnapshotWithoutReloadingOwner() {
        OwnerRepository owners = mock(OwnerRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        AuthController controller = controller(owners, restaurants, mock(PasswordEncoder.class),
                mock(JwtService.class));
        Owner owner = owner();
        MockHttpServletRequest request = requestWithOwner(owner);
        when(restaurants.findAllByOwnerIdAndActiveTrue(owner.getId())).thenReturn(List.of());

        var response = controller.me(ownerAuthentication(owner), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(owner.getId(), body.get("ownerId"));
        assertEquals(owner.getFullName(), body.get("fullName"));
        assertEquals(owner.getEmail(), body.get("email"));
        assertEquals(owner.getPhone(), body.get("phone"));
        assertEquals(owner.getAvatarIcon(), body.get("avatarIcon"));
        assertEquals(owner.getAvatarImage(), body.get("avatarImage"));
        assertEquals(List.of(), body.get("restaurants"));
        verifyNoInteractions(owners);
        verify(restaurants).findAllByOwnerIdAndActiveTrue(owner.getId());
    }

    @Test
    void meRejectsInactiveOwnerSnapshot() {
        OwnerRepository owners = mock(OwnerRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        AuthController controller = controller(owners, restaurants, mock(PasswordEncoder.class),
                mock(JwtService.class));
        Owner owner = owner();
        owner.setActive(false);

        var response = controller.me(
                ownerAuthentication(owner),
                requestWithOwner(owner));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(owners, restaurants);
    }

    @Test
    void meRetainsOwnerLookupFallbackWithoutVerifiedRequestSnapshot() {
        OwnerRepository owners = mock(OwnerRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        AuthController controller = controller(owners, restaurants, mock(PasswordEncoder.class),
                mock(JwtService.class));
        Owner owner = owner();
        when(owners.findByEmailAndDeletedAtIsNull(owner.getEmail())).thenReturn(Optional.of(owner));
        when(restaurants.findAllByOwnerIdAndActiveTrue(owner.getId())).thenReturn(List.of());

        var response = controller.me(
                ownerAuthentication(owner),
                new MockHttpServletRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(owners).findByEmailAndDeletedAtIsNull(owner.getEmail());
        verify(restaurants).findAllByOwnerIdAndActiveTrue(owner.getId());
    }

    @Test
    void loginLoadsRestaurantsOnceForResponseAndOnboardingFlag() {
        OwnerRepository owners = mock(OwnerRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        AuthController controller = controller(
                owners, restaurants, passwordEncoder, jwtService);
        Owner owner = owner();
        owner.setPasswordHash("encoded-password");
        when(owners.findByEmailAndDeletedAtIsNull(owner.getEmail())).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(owner.getEmail(), owner.getTokenVersion()))
                .thenReturn("test-token");
        when(restaurants.findAllByOwnerIdAndActiveTrue(owner.getId())).thenReturn(List.of());
        LoginRequest login = new LoginRequest();
        login.setEmail(owner.getEmail());
        login.setPassword("password");

        var response = controller.login(login);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(List.of(), body.get("restaurants"));
        assertEquals(true, body.get("onboardingRequired"));
        verify(restaurants, times(1)).findAllByOwnerIdAndActiveTrue(owner.getId());
    }

    private static AuthController controller(
            OwnerRepository owners,
            RestaurantRepository restaurants,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        return new AuthController(
                owners,
                restaurants,
                passwordEncoder,
                jwtService,
                mock(GoogleIdentityService.class),
                mock(RestaurantService.class),
                mock(PasswordResetService.class));
    }

    private static Owner owner() {
        Owner owner = new Owner();
        owner.setId(17L);
        owner.setFullName("TexOS Owner");
        owner.setEmail("owner@example.com");
        owner.setPhone("5550100");
        owner.setAvatarIcon("TO");
        owner.setAvatarImage("data:image/png;base64,abc");
        owner.setActive(true);
        return owner;
    }

    private static MockHttpServletRequest requestWithOwner(Owner owner) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE,
                AuthenticatedOwnerPrincipal.from(owner));
        return request;
    }

    private static UsernamePasswordAuthenticationToken ownerAuthentication(Owner owner) {
        return new UsernamePasswordAuthenticationToken(
                owner.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    }
}
