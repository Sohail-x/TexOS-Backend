
package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Email or mobile number is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
