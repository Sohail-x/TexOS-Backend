package com.texos.texos_backend.service;

import java.math.BigDecimal;

public record OrderApprovalPendingEvent(
        Long restaurantId, Long orderId, String tableNumber, BigDecimal totalAmount) {
}
