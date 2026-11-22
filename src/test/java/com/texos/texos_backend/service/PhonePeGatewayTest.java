package com.texos.texos_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhonePeGatewayTest {
    private final PhonePeGateway gateway = new PhonePeGateway(new ObjectMapper(), false);

    @Test
    void acceptsValidWebhookSignaturesAndRejectsTamperedPayloads() throws Exception {
        String payload = "{\"event\":\"checkout.order.completed\"}";
        String secret = "test-webhook-secret";
        String signature = signature(secret, payload);

        assertDoesNotThrow(() ->
                gateway.verifyWebhookSignature(payload, "key-1", signature, "key-1", secret));
        assertThrows(SecurityException.class, () ->
                gateway.verifyWebhookSignature(payload + " ", "key-1", signature, "key-1", secret));
        assertThrows(SecurityException.class, () ->
                gateway.verifyWebhookSignature(payload, "other-key", signature, "key-1", secret));
    }

    private String signature(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
