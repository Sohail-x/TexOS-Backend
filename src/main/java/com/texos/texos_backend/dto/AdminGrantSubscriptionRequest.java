package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminGrantSubscriptionRequest {
    @NotNull
    private Long planId;
}
