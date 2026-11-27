package com.texos.texos_backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final String CONTACT_PATH = "/api/public/contact";
    private static final int FIFTEEN_MINUTES = 15 * 60;
    private static final int ONE_HOUR = 60 * 60;

    private static final Map<String, RateLimitPolicy> POLICIES = Map.ofEntries(
            Map.entry("/api/auth/login", new RateLimitPolicy(10, FIFTEEN_MINUTES)),
            Map.entry("/api/auth/google", new RateLimitPolicy(10, FIFTEEN_MINUTES)),
            Map.entry("/api/auth/forgot-password", new RateLimitPolicy(5, FIFTEEN_MINUTES)),
            Map.entry("/api/auth/reset-password", new RateLimitPolicy(5, FIFTEEN_MINUTES)),
            Map.entry("/api/admin/auth/login", new RateLimitPolicy(10, FIFTEEN_MINUTES)),
            Map.entry("/api/owners/register", new RateLimitPolicy(5, ONE_HOUR)),
            Map.entry("/api/public/customers/login", new RateLimitPolicy(25, FIFTEEN_MINUTES)),
            Map.entry("/api/public/customers/register", new RateLimitPolicy(25, ONE_HOUR)),
            Map.entry("/api/kitchen-staff/verify", new RateLimitPolicy(10, FIFTEEN_MINUTES)),
            Map.entry("/api/public/order-approval/verify", new RateLimitPolicy(10, FIFTEEN_MINUTES)),
            Map.entry("/api/public/order-approval/access", new RateLimitPolicy(10, FIFTEEN_MINUTES)),
            Map.entry("/api/public/customers/forgot-password", new RateLimitPolicy(7, FIFTEEN_MINUTES)),
            Map.entry(CONTACT_PATH, new RateLimitPolicy(5, FIFTEEN_MINUTES)));

    private final JdbcTemplate jdbcTemplate;
    private final String hashSecret;

    public AuthRateLimitFilter(
            JdbcTemplate jdbcTemplate,
            @Value("${app.jwt.secret}") String hashSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.hashSecret = hashSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = requestPath(request);
        RateLimitPolicy policy = "POST".equalsIgnoreCase(request.getMethod())
                ? POLICIES.get(path)
                : null;
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            int requestCount = recordRequest(request, path, policy);
            if (requestCount > policy.maxRequests()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", Integer.toString(policy.windowSeconds()));
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        CONTACT_PATH.equals(path)
                                ? "{\"message\":\"Too many contact submissions. Please try again later.\"}"
                                : "{\"message\":\"Too many authentication attempts. Please try again later.\"}");
                return;
            }
        } catch (DataAccessException ex) {
            log.error(CONTACT_PATH.equals(path)
                    ? "Unable to enforce contact request rate limit"
                    : "Unable to enforce authentication request rate limit", ex);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    CONTACT_PATH.equals(path)
                            ? "{\"message\":\"Contact service is temporarily unavailable. Please try again later.\"}"
                            : "{\"message\":\"Authentication is temporarily unavailable. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    public void deleteExpiredBuckets() {
        jdbcTemplate.update("""
                DELETE FROM auth_rate_limit_buckets
                WHERE window_started_at < UTC_TIMESTAMP(6) - INTERVAL 2 HOUR
                """);
    }

    private int recordRequest(
            HttpServletRequest request, String path, RateLimitPolicy policy) {
        String bucketKey = hash(path + "\n" + request.getRemoteAddr());
        jdbcTemplate.update("""
                INSERT INTO auth_rate_limit_buckets
                    (bucket_key, window_started_at, request_count)
                VALUES (?, UTC_TIMESTAMP(6), 1)
                ON DUPLICATE KEY UPDATE
                    request_count = IF(
                        window_started_at <= UTC_TIMESTAMP(6) - INTERVAL ? SECOND,
                        1,
                        LEAST(request_count + 1, ?)
                    ),
                    window_started_at = IF(
                        window_started_at <= UTC_TIMESTAMP(6) - INTERVAL ? SECOND,
                        UTC_TIMESTAMP(6),
                        window_started_at
                    )
                """,
                bucketKey,
                policy.windowSeconds(),
                policy.maxRequests() + 1,
                policy.windowSeconds());
        Integer requestCount = jdbcTemplate.queryForObject(
                "SELECT request_count FROM auth_rate_limit_buckets WHERE bucket_key = ?",
                Integer.class,
                bucketKey);
        if (requestCount == null) {
            throw new IllegalStateException("Authentication rate-limit bucket was not persisted");
        }
        return requestCount;
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return request.getServletPath();
        }
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty()
                && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not securely hash the rate-limit key", ex);
        }
    }

    private record RateLimitPolicy(int maxRequests, int windowSeconds) {
    }
}
