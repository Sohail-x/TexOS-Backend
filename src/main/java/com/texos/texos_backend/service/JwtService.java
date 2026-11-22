
package com.texos.texos_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey secretKey;

    public JwtService(
        @Value("${app.jwt.secret}") String secret
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "JWT secret must contain at least 32 bytes (received " + keyBytes.length
                    + "). Set TEXOS_JWT_SECRET in the backend .env or environment."
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, int tokenVersion) {
        return generateToken(email, null, 24 * 60 * 60 * 1000L, tokenVersion);
    }

    public String generateAdminToken(String username) {
        return generateToken(username, "TEXOS_ADMIN", 8 * 60 * 60 * 1000L, null);
    }

    public String generateKitchenStaffToken(UUID sessionId, Long restaurantId, Date expiresAt) {
        return Jwts.builder()
                .subject(sessionId.toString())
                .claim("role", "KITCHEN_STAFF")
                .claim("restaurantId", restaurantId)
                .issuedAt(new Date())
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    public String generateOrderApprovalStaffToken(UUID sessionId, Long restaurantId, Date expiresAt) {
        return Jwts.builder()
                .subject(sessionId.toString())
                .claim("role", "ORDER_APPROVER")
                .claim("restaurantId", restaurantId)
                .issuedAt(new Date())
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    public String hashKitchenAccessCode(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getEncoded(), "HmacSHA256"));
            byte[] digest = mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not securely hash the kitchen access code", ex);
        }
    }

    public String extractKitchenStaffSessionId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractKitchenStaffRestaurantId(String token) {
        return extractAllClaims(token).get("restaurantId", Long.class);
    }

    public String extractOrderApprovalSessionId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractOrderApprovalRestaurantId(String token) {
        return extractAllClaims(token).get("restaurantId", Long.class);
    }

    private String generateToken(
            String subject, String role, long lifetimeMillis, Integer tokenVersion) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
            .subject(subject)
            .issuedAt(new Date(now))
            .expiration(new Date(now + lifetimeMillis));
        if (role != null) {
            builder.claim("role", role);
        }
        if (tokenVersion != null) {
            builder.claim("tokenVersion", tokenVersion);
        }
        return builder.signWith(secretKey).compact();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isAdminTokenValid(String token, String username) {
        Claims claims = extractAllClaims(token);
        return username.equals(claims.getSubject())
                && "TEXOS_ADMIN".equals(claims.get("role", String.class))
                && claims.getExpiration().after(new Date());
    }

    public boolean isTokenValid(String token, String email, int expectedTokenVersion) {
        Claims claims = extractAllClaims(token);
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);

        return email.equals(claims.getSubject())
            && claims.getExpiration().after(new Date())
            && (tokenVersion == null ? 0 : tokenVersion) == expectedTokenVersion
            && (claims.get("role", String.class) == null
                || "OWNER".equals(claims.get("role", String.class)));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
