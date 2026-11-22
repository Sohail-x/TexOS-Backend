package com.texos.texos_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RestaurantTableResponse {

    private Long tableId;
    private String tableNumber;
    private String qrToken;
    private String qrImageBase64;
    private boolean active;
    private Long restaurantId;
    private LocalDateTime createdAt;
}
