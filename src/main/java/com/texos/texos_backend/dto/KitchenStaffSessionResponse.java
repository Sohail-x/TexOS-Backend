package com.texos.texos_backend.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KitchenStaffSessionResponse {

    private String token;
    private Long restaurantId;
    private String restaurantName;
    private LocalDateTime expiresAt;
}
