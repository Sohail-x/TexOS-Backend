package com.texos.texos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {
    @NotBlank(message = "Email address is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 150)
    private String email;
}
