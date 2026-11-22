package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifySubscriptionPaymentRequest {
    @NotNull
    private Long restaurantId;
    @NotBlank
    private String merchantOrderId;
}
