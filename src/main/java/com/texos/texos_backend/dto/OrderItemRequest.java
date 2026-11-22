package com.texos.texos_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemRequest {

    @NotNull
    private Long itemId;

    @NotNull
    @Min(1)
    @Max(99)
    private Integer quantity;

    @Size(max = 500)
    private String customization;
}