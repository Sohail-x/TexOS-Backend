package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.ContactMessageRequest;
import com.texos.texos_backend.service.ContactMessageService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/contact")
@RequiredArgsConstructor
public class PublicContactController {
    private final ContactMessageService contactMessageService;

    @PostMapping
    public ResponseEntity<Map<String, String>> submit(
            @Valid @RequestBody ContactMessageRequest request) {
        contactMessageService.send(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("message", "Your message has been accepted for delivery."));
    }
}
