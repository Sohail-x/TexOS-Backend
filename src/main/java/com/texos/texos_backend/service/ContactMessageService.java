package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.ContactMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContactMessageService {

    private static final Logger log =
            LoggerFactory.getLogger(ContactMessageService.class);

    private final BrevoEmailService emailService;

    @Value("${app.mail.contact-to:}")
    private String contactTo;

    public ContactMessageService(BrevoEmailService emailService) {
        this.emailService = emailService;
    }

    public void send(ContactMessageRequest request) {

        if (contactTo == null || contactTo.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Contact email is not configured.");
        }

        String senderEmail = request.getEmail().trim();

        String subject = request.getSubject()
                .trim()
                .replaceAll("[\\r\\n]+", " ");

        String text = "A message was submitted through the TexOS contact form.\n\n"
                + "Name: " + request.getName().trim() + "\n"
                + "Email: " + senderEmail + "\n"
                + "Category: " + request.getCategory() + "\n"
                + "Subject: " + subject + "\n\n"
                + "Message:\n" + request.getMessage().trim();

        try {
            emailService.send(
                    contactTo,
                    "TexOS contact: " + subject,
                    text,
                    senderEmail
            );

        } catch (IllegalStateException ex) {
            log.error("Contact email delivery failed: {}", ex.getMessage());

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Your message could not be delivered. Please try again later.",
                    ex
            );
        }
    }
}
