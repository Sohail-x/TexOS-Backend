package com.texos.texos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CustomerAuthResponse {

    private String message;
    private Long customerId;
    private String customerName;
    private String mobileNumber;
    private Long restaurantId;
}