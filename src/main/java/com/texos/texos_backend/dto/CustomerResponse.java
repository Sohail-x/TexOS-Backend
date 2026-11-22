package com.texos.texos_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class CustomerResponse {

    private Long customerId;
    private Long restaurantId;
    private String customerName;
    private String mobileNumber;
    private String email;
    private boolean mobileVerified;
    private LocalDateTime createdAt;
    private Long totalOrders;
    private BigDecimal totalSpent;
}
