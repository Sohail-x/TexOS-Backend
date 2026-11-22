package com.texos.texos_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MenuItemResponse {

    private Long id;
    private String itemName;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private boolean available;
    private Integer displayOrder;
    private Long categoryId;
    private String categoryName;
    private Long restaurantId;
}
