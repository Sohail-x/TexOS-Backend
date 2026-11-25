package com.texos.texos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoyaltyOverviewReward(
        Long id,
        String rewardName,
        String description,
        String rewardType,
        Integer pointsRequired,
        Long menuItemId,
        String menuItemName,
        BigDecimal discountPercentage,
        BigDecimal discountAmount,
        BigDecimal maximumDiscount,
        BigDecimal minimumOrderAmount,
        Integer totalQuantity,
        Integer remainingQuantity,
        Integer maxRedemptions,
        Integer redemptionCount,
        LocalDateTime startDate,
        LocalDateTime expiryDate,
        boolean active) {
}
