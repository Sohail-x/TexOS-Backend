package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleTokenRequest {
    @NotBlank
    private String credential;
}
