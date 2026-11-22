package com.texos.texos_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OrderItemResponse {

    private Long itemId;
    private String itemName;
    private String customization;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}