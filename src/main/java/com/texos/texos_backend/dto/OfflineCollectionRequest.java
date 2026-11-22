package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OfflineCollectionRequest {
    @NotNull
    private Long orderId;

    @NotBlank
    @Pattern(regexp = "CASH|OFFLINE_UPI")
    private String method;
}
