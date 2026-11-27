package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountDeletionRequest(
        @NotBlank String confirmation
) {
}
