package com.texos.texos_backend.dto;

import com.texos.texos_backend.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest {

    @NotNull
    private OrderStatus status;
}