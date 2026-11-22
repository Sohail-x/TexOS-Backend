package com.texos.texos_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MenuImportItem {

    @NotBlank
    @Size(max = 150)
    private String itemName;

    @Size(max = 100)
    private String categoryName;

    @Size(max = 1000)
    private String description;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal price;
}
