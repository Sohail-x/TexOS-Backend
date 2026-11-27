package com.texos.texos_backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ComplianceRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRateLimitFilter.class);
    private static final String COMPLIANCE_PATH = "/api/admin/compliance";
    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_REQUESTS = 5;
    private static final String TOO_MANY_REQUESTS_MESSAGE =
            "{\"message\":\"Too many compliance export requests. Please wait a minute before trying again.\"}";

    private final JdbcTemplate jdbcTemplate;
    private final String hashSecret;

    public ComplianceRateLimitFilter(
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
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !isCompliancePath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String adminIdentity = authenticatedAdminIdentity();
            String remoteAddress = request.getRemoteAddr();
            int requestCount = recordRequest(adminIdentity, remoteAddress);
            if (requestCount > MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", Integer.toString(WINDOW_SECONDS));
                writeJson(response, TOO_MANY_REQUESTS_MESSAGE);
                return;
            }
        } catch (DataAccessException exception) {
            log.error("Unable to enforce compliance request rate limit", exception);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            writeJson(response,
                    "{\"message\":\"Compliance service is temporarily unavailable. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int recordRequest(String adminIdentity, String remoteAddress) {
        String bucketKey = hash(COMPLIANCE_PATH + "\n" + adminIdentity + "\n" + remoteAddress);
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
                WINDOW_SECONDS,
                MAX_REQUESTS + 1,
                WINDOW_SECONDS);
        Integer requestCount = jdbcTemplate.queryForObject(
                "SELECT request_count FROM auth_rate_limit_buckets WHERE bucket_key = ?",
                Integer.class,
                bucketKey);
        if (requestCount == null) {
            throw new IllegalStateException("Compliance rate-limit bucket was not persisted.");
        }
        return requestCount;
    }

    private String authenticatedAdminIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_TEXOS_ADMIN"::equals)
                && authentication.getName() != null
                && !authentication.getName().isBlank()) {
            return "admin:" + authentication.getName();
        }
        return "ip-only";
    }

    private boolean isCompliancePath(String path) {
        return COMPLIANCE_PATH.equals(path) || path.startsWith(COMPLIANCE_PATH + "/");
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
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not securely hash the compliance rate-limit key.",
                    exception);
        }
    }

    private void writeJson(HttpServletResponse response, String body) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
