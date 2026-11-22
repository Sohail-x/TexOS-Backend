package com.texos.texos_backend.dto;

import com.texos.texos_backend.entity.MenuPriceTaxMode;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RestaurantBillingSettingsRequest {

    @NotNull
    private MenuPriceTaxMode menuPriceTaxMode;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal taxRate;

    @NotNull
    private Boolean serviceChargeEnabled;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal serviceChargeRate;
}