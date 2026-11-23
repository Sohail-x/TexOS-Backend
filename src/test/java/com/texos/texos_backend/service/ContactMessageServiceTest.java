package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.texos.texos_backend.dto.ContactMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ContactMessageServiceTest {
    private final BrevoEmailService emailService = mock(BrevoEmailService.class);
    private final ContactMessageService service = new ContactMessageService(emailService);

    @BeforeEach
    void configureMail() {
        ReflectionTestUtils.setField(service, "contactTo", "support@example.com");
    }

    @Test
    void sendsContactMessageToSupportAndSetsReplyToTheCustomer() {
        ContactMessageRequest request = request();

        service.send(request);

        verify(emailService).send(
                org.mockito.ArgumentMatchers.eq("support@example.com"),
                org.mockito.ArgumentMatchers.eq("TexOS contact: Help with setup"),
                org.mockito.ArgumentMatchers.argThat(text -> text != null
                        && text.contains("General Enquiries")
                        && text.contains("Please contact me.")),
                org.mockito.ArgumentMatchers.eq("customer@example.com")
        );
    }

    @Test
    void reportsUnavailableWhenContactEmailIsNotConfigured() {
        ReflectionTestUtils.setField(service, "contactTo", "");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.send(request()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void reportsUnavailableWhenBrevoRejectsTheMessage() {
        doThrow(new IllegalStateException("Brevo unavailable"))
            .when(emailService).send(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString()
        );

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.send(request())
        );

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
