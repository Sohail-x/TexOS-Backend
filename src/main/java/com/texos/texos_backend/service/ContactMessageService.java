package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.ContactMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContactMessageService {
    private static final Logger log = LoggerFactory.getLogger(ContactMessageService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${app.mail.contact-to:}")
    private String contactTo;

    public ContactMessageService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(ContactMessageRequest request) {
        if (mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || !mailPassword.matches("[A-Za-z0-9]{16}")
                || mailFrom == null || mailFrom.isBlank()
                || contactTo == null || contactTo.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Contact form email delivery is not configured.");
        }

        String senderEmail = request.getEmail().trim();
        String subject = request.getSubject().trim().replaceAll("[\\r\\n]+", " ");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(contactTo);
        message.setReplyTo(senderEmail);
        message.setSubject("TexOS contact: " + subject);
        message.setText("A message was submitted through the TexOS contact form.\n\n"
                + "Name: " + request.getName().trim() + "\n"
                + "Email: " + senderEmail + "\n"
                + "Category: " + request.getCategory() + "\n"
                + "Subject: " + subject + "\n\n"
                + "Message:\n" + request.getMessage().trim());
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            Throwable rootCause = ex;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            log.error("Contact form email delivery failed (cause type: {}).",
                    rootCause.getClass().getSimpleName());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Your message could not be delivered. Please try again later.", ex);
        }
    }
}
