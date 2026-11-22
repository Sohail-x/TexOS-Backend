package com.texos.texos_backend.dto;

import com.texos.texos_backend.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePaymentStatusRequest {
    @NotNull
    private PaymentStatus paymentStatus;
}
