package com.texos.texos_backend.dto;

import java.time.LocalDateTime;

public record LoyaltyTransactionSummary(
        Long id,
        String type,
        Long points,
        String description,
        String orderId,
        LocalDateTime createdAt) {
}
