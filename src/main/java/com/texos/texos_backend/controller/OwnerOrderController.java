package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.dto.BillResponse;
import com.texos.texos_backend.dto.UpdateOrderStatusRequest;
import com.texos.texos_backend.dto.UpdatePaymentStatusRequest;

import com.texos.texos_backend.service.OrderService;
import com.texos.texos_backend.service.CustomerBillService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/orders")
@RequiredArgsConstructor
public class OwnerOrderController {

    private final OrderService orderService;
    private final CustomerBillService customerBillService;

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        if (page != null || size != null || from != null || to != null) {
            return ResponseEntity.ok(
                    orderService.getOrdersForOwner(
                            restaurantId,
                            page == null ? 0 : page,
                            size == null ? 100 : size,
                            from,
                            to
                    )
            );
        }
        return ResponseEntity.ok(
                orderService.getOrdersForOwner(restaurantId)
        );
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long restaurantId,
            @PathVariable Long orderId
    ) {

        return ResponseEntity.ok(
                orderService.getOrderForOwner(
                    restaurantId,
                    orderId
                )
        );
    }

    @GetMapping("/{orderId}/bill")
    public ResponseEntity<BillResponse> getBill(
            @PathVariable Long restaurantId,
            @PathVariable Long orderId
    ) {

        return ResponseEntity.ok(
                customerBillService.getBillForOwner(
                    restaurantId,
                    orderId
                )
        );
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long restaurantId,
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {

        return ResponseEntity.ok(
                orderService.updateOrderStatus(
                    restaurantId,
                    orderId,
                    request.getStatus()
                )
        );
    }

    @PatchMapping("/{orderId}/payment-status")
    public ResponseEntity<OrderResponse> updatePaymentStatus(
            @PathVariable Long restaurantId,
            @PathVariable Long orderId,
            @Valid @RequestBody UpdatePaymentStatusRequest request
    ) {
        return ResponseEntity.ok(orderService.updatePaymentStatus(
                restaurantId, orderId, request.getPaymentStatus()));
    }

}
