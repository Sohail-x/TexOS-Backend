package com.texos.texos_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texos.texos_backend.dto.ContactMessageRequest;
import com.texos.texos_backend.service.ContactMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class PublicContactControllerTest {
    private final ContactMessageService service = mock(ContactMessageService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicContactController(service))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.destroy();
    }

    @Test
    void acceptsValidContactMessageOnlyAfterServiceReturns() throws Exception {
        ContactMessageRequest request = request();

        mockMvc.perform(post("/api/public/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("Your message has been accepted for delivery."));

        verify(service).send(any(ContactMessageRequest.class));
    }

    @Test
    void rejectsUnsupportedCategoriesWithoutSendingEmail() throws Exception {
        ContactMessageRequest request = request();
        request.setCategory("Other");

        mockMvc.perform(post("/api/public/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
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
