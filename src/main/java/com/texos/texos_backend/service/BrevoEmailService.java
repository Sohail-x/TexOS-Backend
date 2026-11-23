package com.texos.texos_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(BrevoEmailService.class);

    private static final URI BREVO_URI =
            URI.create("https://api.brevo.com/v3/smtp/email");

    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private final ObjectMapper objectMapper;

    @Value("${app.mail.brevo-api-key:}")
    private String apiKey;

    @Value("${app.mail.from:}")
    private String senderEmail;

    @Value("${app.mail.sender-name:TexOS}")
    private String senderName;

    public BrevoEmailService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void send(String recipient, String subject, String textContent) {
        send(recipient, subject, textContent, null);
    }

    public void send(
            String recipient,
            String subject,
            String textContent,
            String replyTo) {

        if (apiKey == null || apiKey.isBlank()
                || senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalStateException(
                    "Brevo email configuration is missing.");
        }

        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", senderName);
        sender.put("email", senderEmail);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);
        payload.put("to", List.of(Map.of("email", recipient)));
        payload.put("subject", subject);
        payload.put("textContent", textContent);

        if (replyTo != null && !replyTo.isBlank()) {
            payload.put("replyTo", Map.of("email", replyTo));
        }

        try {
            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder(BREVO_URI)
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .header("api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                log.error("Brevo API rejected email. HTTP status: {}",
                        response.statusCode());

                throw new IllegalStateException(
                        "Brevo email request failed with HTTP "
                                + response.statusCode());
            }

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Could not prepare email request.", ex);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Email request interrupted.", ex);

        } catch (IOException ex) {
            log.error("Brevo API connection failed: {}",
                    ex.getClass().getSimpleName());

            throw new IllegalStateException(
                    "Could not connect to Brevo.", ex);
        }
    }
}
