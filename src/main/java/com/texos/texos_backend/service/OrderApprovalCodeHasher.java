package com.texos.texos_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderApprovalCodeHasher {

    private final byte[] hmacKey;

    public OrderApprovalCodeHasher(@Value("${app.jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        try {
            Mac keyDerivation = Mac.getInstance("HmacSHA256");
            keyDerivation.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            hmacKey = keyDerivation.doFinal("order-approval-code-hmac".getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not initialize order approval code hashing", ex);
        }
    }

    public String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not hash the order approval access code", ex);
        }
    }
}
