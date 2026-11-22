package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.KitchenAccessGrant;
import com.texos.texos_backend.entity.KitchenAccessStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.KitchenAccessException;
import com.texos.texos_backend.repository.KitchenAccessGrantRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KitchenAccessServiceTest {

    private final KitchenAccessGrantRepository grants = mock(KitchenAccessGrantRepository.class);
    private final KitchenAccessService service = new KitchenAccessService(
            grants,
            mock(RestaurantRepository.class),
            mock(OwnerRepository.class),
            new JwtService("test-secret-that-is-at-least-32-bytes-long"));

    @Test
    void redeemsCodeOnceAndCreatesRestaurantBoundStaffSession() {
        KitchenAccessGrant grant = activeGrant();
        when(grants.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(grant));

        var session = service.redeemCode("123456", "192.0.2.10");

        assertEquals(KitchenAccessStatus.USED, grant.getStatus());
        assertNotNull(grant.getUsedAt());
        assertEquals(42L, session.getRestaurantId());
        assertEquals("Kitchen Demo", session.getRestaurantName());
        assertEquals("KITCHEN_STAFF", serviceJwt().extractRole(session.getToken()));
        assertEquals(42L, serviceJwt().extractKitchenStaffRestaurantId(session.getToken()));
        verify(grants).save(grant);

        KitchenAccessException error = assertThrows(
                KitchenAccessException.class,
                () -> service.redeemCode("123456", "192.0.2.11"));
        assertEquals(409, error.getStatus().value());
    }

    @Test
    void rejectsExpiredCodeAndMarksItExpired() {
        KitchenAccessGrant grant = activeGrant();
        grant.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(grants.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(grant));

        KitchenAccessException error = assertThrows(
                KitchenAccessException.class,
                () -> service.redeemCode("123456", "192.0.2.12"));

        assertEquals(410, error.getStatus().value());
        assertEquals(KitchenAccessStatus.EXPIRED, grant.getStatus());
        verify(grants).save(grant);
    }

    @Test
    void rateLimitsRepeatedInvalidCodes() {
        when(grants.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(
                    KitchenAccessException.class,
                    () -> service.redeemCode("654321", "192.0.2.13"));
        }

        KitchenAccessException limited = assertThrows(
                KitchenAccessException.class,
                () -> service.redeemCode("654321", "192.0.2.13"));
        assertEquals(429, limited.getStatus().value());
    }

    private KitchenAccessGrant activeGrant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(42L);
        restaurant.setRestaurantName("Kitchen Demo");
        KitchenAccessGrant grant = new KitchenAccessGrant();
        grant.setRestaurant(restaurant);
        grant.setTokenHash(serviceJwt().hashKitchenAccessCode("123456"));
        grant.setStatus(KitchenAccessStatus.ACTIVE);
        grant.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return grant;
    }

    private JwtService serviceJwt() {
        return new JwtService("test-secret-that-is-at-least-32-bytes-long");
    }
}
