package com.texos.texos_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MenuItemRequest {

    @NotBlank(message = "Item name is required")
    @Size(max = 150, message = "Item name cannot exceed 150 characters")
    private String itemName;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotNull(message = "Item price is required")
    @DecimalMin(value = "0.0", inclusive = false,
            message = "Price must be greater than zero")
    private BigDecimal price;

    @Size(max = 2_800_000, message = "Image must be 2 MB or smaller")
    private String imageUrl;

    private Integer displayOrder;
}
