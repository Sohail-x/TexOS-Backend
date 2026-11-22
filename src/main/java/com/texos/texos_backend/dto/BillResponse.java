package com.texos.texos_backend.dto;

import com.texos.texos_backend.entity.MenuPriceTaxMode;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.OrderStatus;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class BillResponse {

    private String billNumber;
    private Integer billVersion;

    private Long orderId;
    private String orderNumber;
    private List<Long> orderIds;
    private List<String> orderNumbers;
    private List<String> billNumbers;
    private String restaurantName;
    private String tableNumber;
    private PaymentStatus paymentStatus;
    private List<OrderStatus> orderStatuses;
    private String customerName;
    private String customerMobile;
    private boolean eligibleForOnlinePayment;
    private boolean onlinePaymentAvailable;

    private List<OrderItemResponse> items;

    private BigDecimal subtotal;

    private MenuPriceTaxMode taxMode;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;

    private boolean serviceChargeEnabled;
    private BigDecimal serviceChargeRate;
    private BigDecimal serviceChargeAmount;

    private BigDecimal rewardDiscountAmount;

    private BigDecimal totalAmount;

    private LocalDateTime billDate;
    private Instant expiresAt;
    private String paymentQrImage;
    private String upiId;
}