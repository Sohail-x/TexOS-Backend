package com.texos.texos_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MenuCategoryResponse {

    private Long id;
    private String categoryName;
    private String description;
    private boolean active;
    private Integer displayOrder;
    private Long restaurantId;
}
