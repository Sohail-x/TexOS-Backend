package com.texos.texos_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class GoogleIdentityService {
    private final String clientId;
    private final ObjectMapper objectMapper;

    public GoogleIdentityService(
            @Value("${app.google.client-id:}") String clientId,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.objectMapper = objectMapper;
    }

    public VerifiedGoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Google client ID is not configured");
        }
        try {
            String encoded = URLEncoder.encode(idToken, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://oauth2.googleapis.com/tokeninfo?id_token=" + encoded))
                    .GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("Google ID token is invalid");
            }
            JsonNode claims = objectMapper.readTree(response.body());
            String issuer = claims.path("iss").asText();
            String verified = claims.path("email_verified").asText();
            long expiry = claims.path("exp").asLong(0);
            if (!(issuer.equals("https://accounts.google.com") || issuer.equals("accounts.google.com"))
                    || !clientId.equals(claims.path("aud").asText())
                    || expiry <= Instant.now().getEpochSecond()
                    || !"true".equalsIgnoreCase(verified)
                    || claims.path("sub").asText().isBlank()
                    || claims.path("email").asText().isBlank()) {
                throw new IllegalArgumentException("Google ID token claims are invalid");
            }
            return new VerifiedGoogleIdentity(
                    claims.path("sub").asText(),
                    claims.path("email").asText().trim().toLowerCase(),
                    claims.path("name").asText("TexOS User"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google identity verification was interrupted", ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify Google identity", ex);
        }
    }

    public record VerifiedGoogleIdentity(String subject, String email, String name) {}
}
