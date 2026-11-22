
package com.texos.texos_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PublicMenuCategoryResponse {

    private Long id;
    private String categoryName;
    private String description;
    private Integer displayOrder;

    private List<PublicMenuItemResponse> items;
}
