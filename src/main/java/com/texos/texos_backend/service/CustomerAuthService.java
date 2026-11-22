
package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.CustomerAuthResponse;
import com.texos.texos_backend.dto.CustomerLoginRequest;
import com.texos.texos_backend.dto.CustomerRegisterRequest;
import com.texos.texos_backend.entity.CustomerAccountIdentity;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.repository.CustomerAccountIdentityRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private final CustomerRepository customerRepository;
    private final CustomerAccountIdentityRepository identityRepository;
    private final RestaurantTableRepository tableRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CustomerAuthResponse register(CustomerRegisterRequest request) {

        // Find restaurant through QR token
        RestaurantTable table = tableRepository
                .findByQrTokenAndActiveTrue(request.getQrToken())
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid or inactive QR code"));

        if (!table.getRestaurant().isActive()) {
            throw new IllegalArgumentException("Restaurant is inactive");
        }

        Long restaurantId = table.getRestaurant().getId();

        String mobileNumber = normalizeMobile(request.getMobileNumber());
        String email = normalizeEmail(request.getEmail());

        if (identityRepository.existsByIdentityTypeAndIdentityValue("MOBILE", mobileNumber)) {
            throw new DuplicateResourceException("Account already exists with this mobile number. Please sign in.");
        }
        if (email != null && identityRepository.existsByIdentityTypeAndIdentityValue("EMAIL", email)) {
            throw new DuplicateResourceException("Account already exists with this email. Please sign in.");
        }


        Customer customer = new Customer();

        customer.setCustomerName(request.getFullName().trim());
        customer.setMobileNumber(mobileNumber);
        customer.setEmail(email);

        // Always store the encoded password
        customer.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        customer.setMobileVerified(false);
        customer.setRestaurant(table.getRestaurant());

        Customer savedCustomer = customerRepository.save(customer);
        try {
            saveIdentity(savedCustomer, "MOBILE", mobileNumber);
            if (email != null) saveIdentity(savedCustomer, "EMAIL", email);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Account already exists with this mobile number or email. Please sign in.");
        }

        return buildResponse(savedCustomer, "Registration successful");
    }

    @Transactional(readOnly = true)
    public CustomerAuthResponse login(CustomerLoginRequest request) {

        // Identify restaurant through QR token
        RestaurantTable table = tableRepository
                .findByQrTokenAndActiveTrue(request.getQrToken())
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid or inactive QR code"));

        if (!table.getRestaurant().isActive()) {
            throw new IllegalArgumentException("Restaurant is inactive");
        }

        Long restaurantId = table.getRestaurant().getId();

        String mobileNumber = normalizeMobile(request.getMobileNumber());

        Customer customer = customerRepository
                .findByRestaurant_IdAndMobileNumber(
                        restaurantId, mobileNumber)
                .or(() -> customerRepository.findByRestaurant_IdAndMobileNumber(
                        restaurantId, request.getMobileNumber().trim()))
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account does not exist. Please sign up first."));

        if (customer.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), customer.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "Invalid mobile number or password");
        }

        return buildResponse(customer, "Login successful");
    }

    private void saveIdentity(Customer customer, String type, String value) {
        CustomerAccountIdentity identity = new CustomerAccountIdentity();
        identity.setIdentityType(type);
        identity.setIdentityValue(value);
        identity.setCustomer(customer);
        identityRepository.saveAndFlush(identity);
    }

    private String normalizeMobile(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() < 7 || digits.length() > 15) {
            throw new IllegalArgumentException("Enter a valid mobile number");
        }
        return digits;
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private CustomerAuthResponse buildResponse(
            Customer customer, String message) {

        return new CustomerAuthResponse(
                message,
                customer.getId(),
                customer.getCustomerName(),
                customer.getMobileNumber(),
                customer.getRestaurant().getId()
        );
    }
}
