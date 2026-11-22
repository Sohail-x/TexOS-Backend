package com.texos.texos_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PhonePeGateway {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean partnerIntegration;
    private final Map<String, CachedToken> accessTokens = new ConcurrentHashMap<>();

    public PhonePeGateway(
            ObjectMapper objectMapper,
            @Value("${app.phonepe.partner-integration:false}") boolean partnerIntegration) {
        this.objectMapper = objectMapper;
        this.partnerIntegration = partnerIntegration;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public CreatedPayment createPayment(
            String cacheKey,
            PhonePeMerchantCredentials credentials,
            String merchantOrderId,
            long amountMinor,
            String redirectUrl) {
        String url = apiBase(credentials.environment()) + "/checkout/v2/pay";
        Map<String, Object> body = Map.of(
                "merchantOrderId", merchantOrderId,
                "amount", amountMinor,
                "expireAfter", 1200,
                "paymentFlow", Map.of(
                        "type", "PG_CHECKOUT",
                        "merchantUrls", Map.of("redirectUrl", redirectUrl)));
        JsonNode response = sendJson(cacheKey, credentials, "POST", url, body);
        String orderId = response.path("orderId").asText("");
        String state = response.path("state").asText("");
        String checkoutUrl = response.path("redirectUrl").asText("");
        if (orderId.isBlank() || checkoutUrl.isBlank() || !"PENDING".equalsIgnoreCase(state)) {
            throw new IllegalStateException("PhonePe did not return a pending checkout session");
        }
        return new CreatedPayment(orderId, checkoutUrl, state);
    }

    public VerifiedPayment getOrderStatus(
            String cacheKey,
            PhonePeMerchantCredentials credentials,
            String merchantOrderId,
            long expectedAmountMinor) {
        String url = apiBase(credentials.environment()) + "/checkout/v2/order/"
                + URLEncoder.encode(merchantOrderId, StandardCharsets.UTF_8)
                + "/status?details=true";
        JsonNode response = sendJson(cacheKey, credentials, "GET", url, null);
        String responseMerchantOrderId = response.path("merchantOrderId").asText("");
        if (!responseMerchantOrderId.isBlank() && !merchantOrderId.equals(responseMerchantOrderId)) {
            throw new IllegalStateException("PhonePe returned a different merchant order ID");
        }
        long amount = response.path("amount").asLong(-1);
        if (amount != expectedAmountMinor) {
            throw new IllegalStateException("PhonePe order amount does not match the TexOS payment amount");
        }
        String state = response.path("state").asText("UNKNOWN").toUpperCase(java.util.Locale.ROOT);
        JsonNode detail = completedPaymentDetail(response.path("paymentDetails"));
        String method = detail.path("paymentMode").asText("");
        String reference = detail.path("transactionId").asText("");
        long timestamp = detail.path("timestamp").asLong(0);
        LocalDateTime paidAt = timestamp > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
                : null;
        String errorCode = detail.path("errorCode").asText(response.path("errorCode").asText(""));
        return new VerifiedPayment(
                state,
                amount,
                response.path("merchantId").asText(""),
                response.path("orderId").asText(""),
                method.isBlank() ? null : method,
                reference.isBlank() ? null : reference,
                paidAt,
                errorCode.isBlank() ? null : errorCode);
    }

    public void verifyWebhookSignature(
            String rawBody,
            String keyId,
            String signature,
            String configuredKeyId,
            String configuredSecret) {
        if (keyId == null || signature == null || configuredKeyId == null || configuredSecret == null
                || !MessageDigest.isEqual(keyId.getBytes(StandardCharsets.UTF_8),
                        configuredKeyId.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid PhonePe webhook key ID");
        }
        byte[] digest = hmacSha256(configuredSecret, rawBody);
        String hex = java.util.HexFormat.of().formatHex(digest);
        String base64 = Base64.getEncoder().encodeToString(digest);
        if (!constantTimeEquals(hex, signature) && !constantTimeEquals(base64, signature)) {
            throw new SecurityException("Invalid PhonePe webhook signature");
        }
    }

    public String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash PhonePe webhook payload", ex);
        }
    }

    private JsonNode sendJson(
            String cacheKey,
            PhonePeMerchantCredentials credentials,
            String method,
            String url,
            Map<String, Object> body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "O-Bearer " + accessToken(cacheKey, credentials))
                    .header("Content-Type", "application/json");
            if (partnerIntegration && credentials.merchantId() != null
                    && !credentials.merchantId().isBlank()) {
                request.header("X-MERCHANT-ID", credentials.merchantId());
            }
            if ("GET".equals(method)) {
                request.GET();
            } else {
                request.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PhonePeRequestException(
                        "PhonePe request failed (HTTP " + response.statusCode() + ")",
                        response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PhonePe request was interrupted", ex);
        } catch (PhonePeRequestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new PhonePeTransportException("PhonePe request could not be completed", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to communicate with PhonePe", ex);
        }
    }

    private String accessToken(String cacheKey, PhonePeMerchantCredentials credentials) {
        String credentialKey = sha256(String.join("\u0000",
                cacheKey,
                credentials.merchantId() == null ? "" : credentials.merchantId(),
                credentials.clientId(),
                credentials.clientSecret(),
                Integer.toString(credentials.clientVersion()),
                credentials.environment()));
        CachedToken cached = accessTokens.get(credentialKey);
        if (cached != null && Instant.now().isBefore(cached.expiresAt().minusSeconds(60))) {
            return cached.value();
        }
        String endpoint = "SANDBOX".equalsIgnoreCase(credentials.environment())
                ? "https://api-preprod.phonepe.com/apis/pg-sandbox/v1/oauth/token"
                : "https://api.phonepe.com/apis/identity-manager/v1/oauth/token";
        String form = "client_id=" + formValue(credentials.clientId())
                + "&client_version=" + credentials.clientVersion()
                + "&client_secret=" + formValue(credentials.clientSecret())
                + "&grant_type=client_credentials";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PhonePeRequestException(
                        "PhonePe authorization failed (HTTP " + response.statusCode() + ")",
                        response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String value = json.path("access_token").asText("");
            long expiresAt = json.path("expires_at").asLong(0);
            if (value.isBlank() || expiresAt <= Instant.now().getEpochSecond()) {
                throw new IllegalStateException("PhonePe returned an invalid authorization token");
            }
            CachedToken token = new CachedToken(value, Instant.ofEpochSecond(expiresAt));
            accessTokens.put(credentialKey, token);
            return value;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PhonePe authorization was interrupted", ex);
        } catch (PhonePeRequestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new PhonePeTransportException("PhonePe authorization could not be completed", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to authorize with PhonePe", ex);
        }
    }

    private String apiBase(String environment) {
        return "SANDBOX".equalsIgnoreCase(environment)
                ? "https://api-preprod.phonepe.com/apis/pg-sandbox"
                : "https://api.phonepe.com/apis/pg";
    }

    private String formValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode completedPaymentDetail(JsonNode details) {
        if (details != null && details.isArray()) {
            for (JsonNode detail : details) {
                if ("COMPLETED".equalsIgnoreCase(detail.path("state").asText())) return detail;
            }
        }
        return objectMapper.createObjectNode();
    }

    private byte[] hmacSha256(String secret, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify PhonePe webhook signature", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public record CreatedPayment(String providerOrderId, String checkoutUrl, String state) {}

    public record VerifiedPayment(
            String state,
            long amountMinor,
            String merchantId,
            String providerOrderId,
            String paymentMethod,
            String paymentReference,
            LocalDateTime paidAt,
            String errorCode) {}

    private record CachedToken(String value, Instant expiresAt) {}

    public static class PhonePeRequestException extends IllegalStateException {
        private final int httpStatus;

        public PhonePeRequestException(String message, int httpStatus) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }

    public static class PhonePeTransportException extends IllegalStateException {
        public PhonePeTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
