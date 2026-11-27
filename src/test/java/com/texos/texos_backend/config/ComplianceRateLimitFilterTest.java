package com.texos.texos_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ComplianceRateLimitFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksTheSixteenthComplianceRequestWithTheDocumented429Response() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doReturn(16).when(jdbcTemplate).queryForObject(
                anyString(), eq(Integer.class), any(Object[].class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "texos-admin",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_TEXOS_ADMIN"))));
        ComplianceRateLimitFilter filter = new ComplianceRateLimitFilter(
                jdbcTemplate, "test-secret-that-is-at-least-32-bytes-long");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean requestContinued = new AtomicBoolean();

        filter.doFilter(complianceRequest("GET", "/api/admin/compliance/deletion-requests"),
                response, (request, result) -> requestContinued.set(true));

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains(
                "Too many compliance export requests. Please wait a minute before trying again."));
        assertFalse(requestContinued.get());
    }

    @Test
    void doesNotCountCorsPreflightOrOtherAdminPaths() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ComplianceRateLimitFilter filter = new ComplianceRateLimitFilter(
                jdbcTemplate, "test-secret-that-is-at-least-32-bytes-long");
        AtomicBoolean preflightContinued = new AtomicBoolean();
        AtomicBoolean otherPathContinued = new AtomicBoolean();

        filter.doFilter(complianceRequest("OPTIONS", "/api/admin/compliance/search"),
                new MockHttpServletResponse(),
                (request, response) -> preflightContinued.set(true));
        filter.doFilter(complianceRequest("GET", "/api/admin/announcements"),
                new MockHttpServletResponse(),
                (request, response) -> otherPathContinued.set(true));

        assertTrue(preflightContinued.get());
        assertTrue(otherPathContinued.get());
        verifyNoInteractions(jdbcTemplate);
    }

    private MockHttpServletRequest complianceRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setServletPath(path);
        request.setRemoteAddr("203.0.113.8");
        return request;
    }
}
