package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminJwtServiceTest {

    private final JwtService jwtService =
            new JwtService("test-secret-that-is-at-least-32-bytes-long");

    @Test
    void adminTokenHasAnIsolatedRoleAndExpiresWithinEightHours() {
        String token = jwtService.generateAdminToken("texos-admin");

        assertEquals("TEXOS_ADMIN", jwtService.extractRole(token));
        assertTrue(jwtService.isAdminTokenValid(token, "texos-admin"));
        assertFalse(jwtService.isAdminTokenValid(token, "another-admin"));
        assertFalse(jwtService.isTokenValid(token, "texos-admin", 0));
    }

    @Test
    void ownerTokenDoesNotHaveAdminRole() {
        String token = jwtService.generateToken("owner@example.com", 0);

        assertNull(jwtService.extractRole(token));
        assertTrue(jwtService.isTokenValid(token, "owner@example.com", 0));
        assertFalse(jwtService.isTokenValid(token, "owner@example.com", 1));
        assertFalse(jwtService.isAdminTokenValid(token, "owner@example.com"));
    }
}
