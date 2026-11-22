package com.texos.texos_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateOrderRequest {

    @NotBlank
    private String qrToken;

    @Size(max = 100, message = "Customer name cannot exceed 100 characters")
    private String customerName;

    @Size(max = 16, message = "Mobile number cannot exceed 16 characters")
    @Pattern(regexp = "^\\+?[0-9\\s()\\-]{7,20}$", message = "Enter a valid mobile number")
    private String customerMobile;

    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String customerEmail;

    private Long rewardId;
    private Integer rewardPoints;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
}
