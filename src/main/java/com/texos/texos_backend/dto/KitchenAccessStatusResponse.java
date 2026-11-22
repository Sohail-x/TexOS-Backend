package com.texos.texos_backend.dto;

import com.texos.texos_backend.entity.KitchenAccessStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KitchenAccessStatusResponse {

    private KitchenAccessStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private boolean sessionActive;
}
