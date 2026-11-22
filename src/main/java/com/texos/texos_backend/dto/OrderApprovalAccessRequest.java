package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrderApprovalAccessRequest(
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
}
