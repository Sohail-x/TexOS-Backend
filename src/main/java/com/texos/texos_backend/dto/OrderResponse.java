package com.texos.texos_backend.dto;

import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class OrderResponse {

    private Long id;
    private String orderNumber;

    private String restaurantName;
    private String tableNumber;
    private String customerName;

    private OrderStatus orderStatus;
    private OrderApprovalStatus approvalStatus;
    private PaymentStatus paymentStatus;

    private List<OrderItemResponse> items;

    private BigDecimal totalAmount;

    private LocalDateTime createdAt;

    private BillResponse bill;
}