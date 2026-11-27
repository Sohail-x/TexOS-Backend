
package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.CustomerAuthResponse;
import com.texos.texos_backend.dto.CustomerLoginRequest;
import com.texos.texos_backend.dto.CustomerRegisterRequest;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.CustomerAccountIdentity;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.repository.CustomerAccountIdentityRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.CustomerPaymentTransactionRepository;
import com.texos.texos_backend.repository.LoyaltyAccountRepository;
import com.texos.texos_backend.repository.LoyaltyPointLotRepository;
import com.texos.texos_backend.repository.OfflineAssistanceRequestRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private final CustomerRepository customerRepository;
    private final CustomerAccountIdentityRepository identityRepository;
    private final RestaurantTableRepository tableRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyPointLotRepository loyaltyPointLotRepository;
    private final RestaurantOrderRepository orderRepository;
    private final CustomerPaymentTransactionRepository paymentTransactionRepository;
    private final OfflineAssistanceRequestRepository assistanceRequestRepository;

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

    @Transactional
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
                .findByRestaurant_IdAndMobileNumberAndActiveTrueAndDeletedAtIsNull(
                        restaurantId, mobileNumber)
                .or(() -> customerRepository.findByRestaurant_IdAndMobileNumberAndActiveTrueAndDeletedAtIsNull(
                        restaurantId, request.getMobileNumber().trim()))
                .orElse(null);

        boolean newlyProvisioned = false;
        if (customer != null) {
            verifyPassword(request.getPassword(), customer);
        } else {
            RestaurantProfile profile = provisionRestaurantProfile(
                    restaurantId, table.getRestaurant(), mobileNumber, request.getPassword());
            customer = profile.customer();
            newlyProvisioned = profile.created();
        }

        if (!newlyProvisioned) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            customerRepository.touchLastActivity(
                    customer.getId(), now.minusDays(1), now);
        }
        return buildResponse(customer, "Login successful");
    }

    private RestaurantProfile provisionRestaurantProfile(
            Long restaurantId, Restaurant restaurant,
            String mobileNumber, String rawPassword) {
        CustomerAccountIdentity identity = identityRepository
                .findByIdentityTypeAndIdentityValue("MOBILE", mobileNumber)
                .orElseThrow(CustomerAuthService::accountDoesNotExist);

        Customer existingLocalCustomer = customerRepository
                .findByRestaurant_IdAndMobileNumberAndActiveTrueAndDeletedAtIsNull(
                        restaurantId, mobileNumber)
                .orElse(null);
        if (existingLocalCustomer != null) {
            verifyPassword(rawPassword, existingLocalCustomer);
            return new RestaurantProfile(existingLocalCustomer, false);
        }
        if (customerRepository.findByRestaurant_IdAndMobileNumber(restaurantId, mobileNumber)
                .isPresent()) {
            throw accountDoesNotExist();
        }

        Customer source = identity.getCustomer();
        if (!source.isActive() || source.getDeletedAt() != null
                || source.getAnonymizedAt() != null) {
            throw accountDoesNotExist();
        }
        verifyPassword(rawPassword, source);
        if (source.getRestaurant().getId().equals(restaurantId)) {
            return new RestaurantProfile(source, false);
        }

        Customer localCustomer = new Customer();
        localCustomer.setCustomerName(source.getCustomerName());
        localCustomer.setMobileNumber(mobileNumber);
        localCustomer.setEmail(source.getEmail());
        localCustomer.setPasswordHash(source.getPasswordHash());
        localCustomer.setMobileVerified(false);
        localCustomer.setRestaurant(restaurant);
        localCustomer.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC));
        Customer savedLocalCustomer = customerRepository.save(localCustomer);
        List<CustomerAccountIdentity> identities =
                identityRepository.findAllByCustomer_Id(source.getId());
        identities.forEach(accountIdentity -> accountIdentity.setCustomer(savedLocalCustomer));
        identityRepository.saveAll(identities);
        return new RestaurantProfile(savedLocalCustomer, true);
    }

    private record RestaurantProfile(Customer customer, boolean created) {
    }

    private void verifyPassword(String rawPassword, Customer customer) {
        if (customer.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, customer.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid mobile number or password");
        }
    }

    private static IllegalArgumentException accountDoesNotExist() {
        return new IllegalArgumentException("Account does not exist. Please sign up first.");
    }

    @Transactional
    public void deleteAccount(Long customerId, Long restaurantId) {
        Customer customer = customerRepository
                .findByIdAndRestaurant_IdForUpdate(customerId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Customer account not found"));
        if (!customer.isActive()) {
            return;
        }

        identityRepository.deleteAllByCustomer_Id(customerId);
        orderRepository.anonymizeCustomerDetails(customerId);
        paymentTransactionRepository.anonymizeCustomerDetails(customerId);
        assistanceRequestRepository.anonymizeCustomerDetails(customerId);

        loyaltyAccountRepository.findByRestaurant_IdAndCustomer_Id(restaurantId, customerId)
                .ifPresent(account -> {
                    account.setAvailablePoints(0L);
                    account.setPointsDebt(0L);
                });
        loyaltyPointLotRepository.forfeitAvailablePoints(customerId);

        customer.setMobileNumber(null);
        customer.setPasswordHash(null);
        customer.setMobileVerified(false);
        customer.setActive(false);
        customer.setDeletedAt(Instant.now());
        customer.setAnonymizedAt(null);
    }

    @Transactional
    public boolean anonymizeForRetention(Long customerId, Instant anonymizedAt) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer account not found"));
        if (customer.getAnonymizedAt() != null) {
            return false;
        }

        Long restaurantId = customer.getRestaurant().getId();
        identityRepository.deleteAllByCustomer_Id(customerId);
        orderRepository.anonymizeCustomerDetails(customerId);
        paymentTransactionRepository.anonymizeCustomerDetails(customerId);
        assistanceRequestRepository.anonymizeCustomerDetails(customerId);
        loyaltyAccountRepository.findByRestaurant_IdAndCustomer_Id(restaurantId, customerId)
                .ifPresent(account -> {
                    account.setAvailablePoints(0L);
                    account.setPointsDebt(0L);
                });
        loyaltyPointLotRepository.forfeitAvailablePoints(customerId);

        customer.setCustomerName("Deleted User");
        customer.setMobileNumber(null);
        customer.setEmail("deleted_" + customerId + "@anonymized.local");
        customer.setPasswordHash(null);
        customer.setMobileVerified(false);
        customer.setActive(false);
        customer.setAnonymizedAt(anonymizedAt);
        return true;
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
