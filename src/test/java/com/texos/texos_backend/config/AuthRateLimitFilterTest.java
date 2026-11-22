package com.texos.texos_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    @Test
    void blocksRequestsOnceThePersistedIpWindowLimitIsExceeded() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doReturn(11).when(jdbcTemplate).queryForObject(
                anyString(), eq(Integer.class), any(Object[].class));
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                jdbcTemplate, "test-secret-that-is-at-least-32-bytes-long");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean requestContinued = new AtomicBoolean();

        filter.doFilter(
                loginRequest(),
                response,
                (request, result) -> requestContinued.set(true));

        assertEquals(429, response.getStatus());
        assertEquals("900", response.getHeader("Retry-After"));
        assertFalse(requestContinued.get());
    }

    @Test
    void rateLimitsContactFormSubmissions() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doReturn(6).when(jdbcTemplate).queryForObject(
                anyString(), eq(Integer.class), any(Object[].class));
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                jdbcTemplate, "test-secret-that-is-at-least-32-bytes-long");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/public/contact");
        request.setServletPath("/api/public/contact");
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean requestContinued = new AtomicBoolean();

        filter.doFilter(request, response, (incoming, result) -> requestContinued.set(true));

        assertEquals(429, response.getStatus());
        assertEquals("900", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("contact submissions"));
        assertFalse(requestContinued.get());
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setRemoteAddr("203.0.113.5");
        return request;
    }
}
