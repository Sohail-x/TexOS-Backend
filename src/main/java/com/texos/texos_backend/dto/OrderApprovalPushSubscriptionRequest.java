package com.texos.texos_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderApprovalPushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotNull @Valid Keys keys) {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }
}
