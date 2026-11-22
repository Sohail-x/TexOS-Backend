package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.CreateOrderRequest;
import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.service.CustomerBillService;
import com.texos.texos_backend.service.OrderService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final OrderService orderService;
    private final CustomerBillService customerBillService;

    @PostMapping
        public ResponseEntity<OrderResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest request,
        HttpServletRequest servletRequest
) {

    OrderResponse response =
            orderService.createOrder(request, servletRequest);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
}

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam String qrToken,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(
                orderService.getCustomerOrders(qrToken, servletRequest));
    }

    @GetMapping("/bill")
    public ResponseEntity<BillResponse> getCurrentBill(
            @RequestParam String qrToken,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(customerBillService.getCurrentBill(qrToken, servletRequest));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable String orderNumber,
            HttpServletRequest servletRequest
    ) {

        return ResponseEntity.ok(
                orderService.getCustomerOrder(orderNumber, servletRequest)
        );
    }
}