package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KitchenAccessCodeRequest {

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private String code;
}
