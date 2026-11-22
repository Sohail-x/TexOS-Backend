package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.service.BillService;
import com.texos.texos_backend.service.OrderService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/orders")
@RequiredArgsConstructor
public class CustomerBillController {

    private final BillService billService;
    private final OrderService orderService;

    @GetMapping("/{orderNumber}/bill")
    public ResponseEntity<BillResponse> getBill(
            @PathVariable String orderNumber,
            HttpServletRequest servletRequest
    ) {

        orderService.getCustomerOrder(orderNumber, servletRequest);
        return ResponseEntity.ok(
                billService.getBillByOrderNumber(orderNumber)
        );
    }
}