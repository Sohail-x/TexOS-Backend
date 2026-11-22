package com.texos.texos_backend.controller;

import com.texos.texos_backend.service.RestaurantPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/orders")
@RequiredArgsConstructor
public class CustomerPaymentController {
    private final RestaurantPaymentService paymentService;

    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> createPayment(
            @RequestParam String qrToken, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createOnlinePayment(qrToken, request));
    }

    @GetMapping("/payment/{merchantOrderId}")
    public ResponseEntity<Map<String, Object>> paymentStatus(
            @PathVariable String merchantOrderId,
            @RequestParam String qrToken,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                paymentService.getCustomerPaymentStatus(merchantOrderId, qrToken, request));
    }

    @GetMapping("/payment-history")
    public ResponseEntity<List<Map<String, Object>>> paymentHistory(
            @RequestParam String qrToken, HttpServletRequest request) {
        return ResponseEntity.ok(paymentService.getCustomerPaymentHistory(qrToken, request));
    }

    @PostMapping("/waiter-request")
    public ResponseEntity<Map<String, Object>> createWaiterRequest(
            @RequestParam String qrToken, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createAssistanceRequest(qrToken, request));
    }

    @PostMapping("/waiter-request/{requestId}/recall")
    public ResponseEntity<Map<String, Object>> recallWaiter(
            @PathVariable Long requestId,
            @RequestParam String qrToken,
            HttpServletRequest request) {
        return ResponseEntity.ok(paymentService.recallWaiter(requestId, qrToken, request));
    }

    @GetMapping("/waiter-request/{requestId}")
    public ResponseEntity<Map<String, Object>> waiterRequestStatus(
            @PathVariable Long requestId,
            @RequestParam String qrToken,
            HttpServletRequest request) {
        return ResponseEntity.ok(paymentService.getAssistanceStatus(requestId, qrToken, request));
    }
}
