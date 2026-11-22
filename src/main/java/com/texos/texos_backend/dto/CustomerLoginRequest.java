package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerLoginRequest {

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "QR token is required")
    private String qrToken;
}