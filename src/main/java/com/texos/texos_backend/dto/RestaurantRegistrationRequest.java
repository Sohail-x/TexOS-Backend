package com.texos.texos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantRegistrationRequest {

    @NotBlank(message = "Restaurant name is required")
    @Size(max = 150, message = "Restaurant name cannot exceed 150 characters")
    private String restaurantName;

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address;

    @Size(max = 20, message = "Phone cannot exceed 20 characters")
    private String phone;

    @Email(message = "Please enter a valid email")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Size(max = 2048, message = "Google Maps link cannot exceed 2048 characters")
    private String googleMapsUrl;

    @Size(max = 100, message = "Opening hours cannot exceed 100 characters")
    private String openingHours;
}