package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotNull;

public record OrderApprovalSettingsRequest(@NotNull Boolean enabled) {
}
