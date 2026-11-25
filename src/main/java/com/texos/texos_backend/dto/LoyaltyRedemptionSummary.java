package com.texos.texos_backend.dto;

import java.time.LocalDateTime;

public record LoyaltyRedemptionSummary(
        Long id,
        String customerName,
        String customerMobile,
        String rewardName,
        Integer pointsUsed,
        String redemptionCode,
        String orderId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime redeemedAt,
        Long rewardId) {
}
