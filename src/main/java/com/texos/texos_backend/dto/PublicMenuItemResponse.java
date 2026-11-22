
package com.texos.texos_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PublicMenuItemResponse {

    private Long id;
    private String itemName;
    private String description;
    private BigDecimal price;
    private String imageUrl;
}
