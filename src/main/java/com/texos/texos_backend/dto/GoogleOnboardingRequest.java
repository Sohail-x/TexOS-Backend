package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleOnboardingRequest {
    @NotBlank
    @Size(max = 150)
    private String restaurantName;

    @NotBlank
    @Size(max = 20)
    private String phone;

    @Size(max = 255)
    private String address;
}
