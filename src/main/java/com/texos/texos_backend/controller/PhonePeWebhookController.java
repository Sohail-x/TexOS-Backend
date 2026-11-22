package com.texos.texos_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texos.texos_backend.service.RestaurantPaymentService;
import com.texos.texos_backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class PhonePeWebhookController {
    private final RestaurantPaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @PostMapping("/phonepe")
    public ResponseEntity<Map<String, String>> phonePe(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-PhonePe-Checksum-Key-Id", required = false) String keyId,
            @RequestHeader(value = "X-PhonePe-Checksum-Signature", required = false) String signature) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String merchantOrderId = root.path("payload").path("merchantOrderId").asText("");
            if (paymentService.isKnownPhonePeOrder(merchantOrderId)) {
                paymentService.acceptPhonePeWebhook(rawBody, keyId, signature);
            } else if (subscriptionService.isKnownPhonePeOrder(merchantOrderId)) {
                subscriptionService.processPhonePeWebhook(rawBody, keyId, signature);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "unknown_order"));
            }
            return ResponseEntity.ok(Map.of("status", "accepted"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "invalid_payload"));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "rejected"));
        }
    }
}
