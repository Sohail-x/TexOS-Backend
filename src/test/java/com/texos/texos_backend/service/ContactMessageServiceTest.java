package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.texos.texos_backend.dto.ContactMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ContactMessageServiceTest {
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final ContactMessageService service = new ContactMessageService(mailSender);

    @BeforeEach
    void configureMail() {
        ReflectionTestUtils.setField(service, "mailUsername", "smtp-user@example.com");
        ReflectionTestUtils.setField(service, "mailPassword", "abcdefghijklmnop");
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@example.com");
        ReflectionTestUtils.setField(service, "contactTo", "support@example.com");
    }

    @Test
    void sendsContactMessageToSupportAndSetsReplyToTheCustomer() {
        ContactMessageRequest request = request();

        service.send(request);

        org.mockito.ArgumentCaptor<SimpleMailMessage> captured =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captured.capture());
        SimpleMailMessage sent = captured.getValue();
        assertEquals("noreply@example.com", sent.getFrom());
        assertEquals("support@example.com", sent.getTo()[0]);
        assertEquals("customer@example.com", sent.getReplyTo());
        assertEquals("TexOS contact: Help with setup", sent.getSubject());
        org.junit.jupiter.api.Assertions.assertTrue(sent.getText().contains("General Enquiries"));
        org.junit.jupiter.api.Assertions.assertTrue(sent.getText().contains("Please contact me."));
    }

    @Test
    void reportsUnavailableWhenContactEmailIsNotConfigured() {
        ReflectionTestUtils.setField(service, "contactTo", "");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.send(request()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void reportsUnavailableWhenSmtpRejectsTheMessage() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.send(request()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    private ContactMessageRequest request() {
        ContactMessageRequest request = new ContactMessageRequest();
        request.setName("Customer Name");
        request.setEmail("customer@example.com");
        request.setSubject("Help with setup");
        request.setCategory("General Enquiries");
        request.setMessage("Please contact me.");
        return request;
    }
}
