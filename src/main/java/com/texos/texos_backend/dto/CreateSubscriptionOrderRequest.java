package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubscriptionOrderRequest {
    @NotNull
    private Long restaurantId;
    @NotNull
    private Long planId;
}
