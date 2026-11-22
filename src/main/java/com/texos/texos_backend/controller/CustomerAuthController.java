
package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.CustomerAuthResponse;
import com.texos.texos_backend.dto.CustomerLoginRequest;
import com.texos.texos_backend.dto.CustomerRegisterRequest;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import com.texos.texos_backend.service.CustomerAuthService;
import com.texos.texos_backend.service.CustomerSession;
import com.texos.texos_backend.service.CustomerSessionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/customers")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerAuthService customerAuthService;
    private final CustomerSessionService customerSessionService;
    private final RestaurantTableRepository tableRepository;
    private final CustomerRepository customerRepository;

    @PostMapping("/register")
    public ResponseEntity<CustomerAuthResponse> register(
            @Valid @RequestBody CustomerRegisterRequest request) {

        CustomerAuthResponse response =
                customerAuthService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<CustomerAuthResponse> login(
            @Valid @RequestBody CustomerLoginRequest request,
            HttpServletRequest httpRequest) {

        CustomerAuthResponse response =
                customerAuthService.login(request);

        customerSessionService.createSession(
                httpRequest,
                response.getCustomerId(),
                response.getRestaurantId()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/session")
    public ResponseEntity<?> checkSession(
            @RequestParam String qrToken,
            HttpServletRequest httpRequest) {

        RestaurantTable table = tableRepository
                .findByQrTokenAndActiveTrue(qrToken)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid or inactive QR code"));

        Long restaurantId = table.getRestaurant().getId();

        CustomerSession activeSession =
                customerSessionService.getActiveSession(
                        httpRequest, restaurantId);

        if (activeSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "authenticated", false,
                            "message", "Customer login required"
                    ));
        }

        Customer customer = customerRepository
                .findByIdAndRestaurant_Id(
                        activeSession.getCustomerId(), restaurantId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Customer account not found"));

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "customerId", activeSession.getCustomerId(),
                "restaurantId", activeSession.getRestaurantId(),
                "customerName", customer.getCustomerName(),
                "mobileNumber", customer.getMobileNumber() != null ? customer.getMobileNumber() : ""
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestParam String qrToken,
            HttpServletRequest httpRequest) {

        RestaurantTable table = tableRepository
                .findByQrTokenAndActiveTrue(qrToken)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid or inactive QR code"));

        customerSessionService.logout(
                httpRequest,
                table.getRestaurant().getId()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully"
        ));
    }
}
