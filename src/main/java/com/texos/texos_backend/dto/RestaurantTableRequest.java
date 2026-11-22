package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantTableRequest {

    @NotBlank(message = "Table number is required")
    @Size(max = 30, message = "Table number cannot exceed 30 characters")
    private String tableNumber;
}
