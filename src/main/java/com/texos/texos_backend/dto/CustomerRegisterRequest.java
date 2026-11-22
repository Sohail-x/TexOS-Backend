package com.texos.texos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerRegisterRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Size(max = 16)
    private String mobileNumber;

    @Email(message = "Invalid email address")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank(message = "QR token is required")
    private String qrToken;
}