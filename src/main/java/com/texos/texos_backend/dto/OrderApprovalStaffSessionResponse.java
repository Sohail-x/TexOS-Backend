package com.texos.texos_backend.dto;

import java.time.LocalDateTime;

public record OrderApprovalStaffSessionResponse(
        String token,
        Long restaurantId,
        String restaurantName,
        LocalDateTime expiresAt) {
}
