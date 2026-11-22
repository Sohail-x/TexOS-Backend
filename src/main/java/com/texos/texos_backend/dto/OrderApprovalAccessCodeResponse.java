package com.texos.texos_backend.dto;

import java.time.LocalDateTime;

public record OrderApprovalAccessCodeResponse(String code, LocalDateTime expiresAt) {
}
