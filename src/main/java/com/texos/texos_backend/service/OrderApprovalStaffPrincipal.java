package com.texos.texos_backend.service;

import java.util.UUID;

public record OrderApprovalStaffPrincipal(Long restaurantId, UUID sessionId) {
}
