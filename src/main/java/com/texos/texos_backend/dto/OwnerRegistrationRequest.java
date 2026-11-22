package com.texos.texos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OwnerRegistrationRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @Size(max = 150)
    @Email(message = "Please enter a valid email")
    private String email;

    @NotBlank(message = "Restaurant name is required")
    @Size(max = 150)
    private String restaurantName;

    @Size(max = 255)
    private String address;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;

    @NotBlank(message = "Mobile number is required")
    @Size(max = 20)
    private String phone;
}