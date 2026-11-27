package com.texos.texos_backend.dto;

import java.math.BigDecimal;

public record PublicMenuItemView(
        Long categoryId,
        Long id,
        String itemName,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean inlineImage
) {
}
