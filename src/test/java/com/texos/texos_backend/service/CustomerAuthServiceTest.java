package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.CustomerLoginRequest;
import com.texos.texos_backend.dto.CustomerRegisterRequest;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.repository.CustomerAccountIdentityRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class CustomerAuthServiceTest {
    private static final long RESTAURANT_ID = 7L;
    private static final String QR_TOKEN = "table-token";
    private static final String MOBILE_NUMBER = "9876543210";

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerAccountIdentityRepository identityRepository =
            mock(CustomerAccountIdentityRepository.class);
    private final RestaurantTableRepository tableRepository =
            mock(RestaurantTableRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CustomerAuthService service = new CustomerAuthService(
            customerRepository, identityRepository, tableRepository, passwordEncoder);

    @Test
    void loginReportsWhenCustomerAccountDoesNotExist() {
        when(tableRepository.findByQrTokenAndActiveTrue(QR_TOKEN))
                .thenReturn(Optional.of(activeTable()));
        when(customerRepository.findByRestaurant_IdAndMobileNumber(
                RESTAURANT_ID, MOBILE_NUMBER)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(loginRequest()));

        assertEquals("Account does not exist. Please sign up first.", error.getMessage());
    }

    @Test
    void signupReportsWhenCustomerAccountAlreadyExists() {
        when(tableRepository.findByQrTokenAndActiveTrue(QR_TOKEN))
                .thenReturn(Optional.of(activeTable()));
        when(identityRepository.existsByIdentityTypeAndIdentityValue(
                "MOBILE", MOBILE_NUMBER)).thenReturn(true);

        CustomerRegisterRequest request = new CustomerRegisterRequest();
        request.setFullName("Asha");
        request.setMobileNumber(MOBILE_NUMBER);
        request.setPassword("password123");
        request.setQrToken(QR_TOKEN);

        DuplicateResourceException error = assertThrows(
                DuplicateResourceException.class,
                () -> service.register(request));

        assertEquals(
                "Account already exists with this mobile number. Please sign in.",
                error.getMessage());
    }

    @Test
    void loginWorksWhenRestaurantRewardsAreDisabled() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setActive(true);

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        when(tableRepository.findByQrTokenAndActiveTrue(QR_TOKEN))
                .thenReturn(Optional.of(table));

        Customer customer = new Customer();
        customer.setId(12L);
        customer.setCustomerName("Asha");
        customer.setMobileNumber(MOBILE_NUMBER);
        customer.setPasswordHash("encoded-password");
        customer.setRestaurant(restaurant);
        when(customerRepository.findByRestaurant_IdAndMobileNumber(
                RESTAURANT_ID, MOBILE_NUMBER)).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        var response = service.login(loginRequest());

        assertEquals("Asha", response.getCustomerName());
        assertEquals(RESTAURANT_ID, response.getRestaurantId());
    }

    private static CustomerLoginRequest loginRequest() {
        CustomerLoginRequest request = new CustomerLoginRequest();
        request.setMobileNumber(MOBILE_NUMBER);
        request.setPassword("password123");
        request.setQrToken(QR_TOKEN);
        return request;
    }

    private static RestaurantTable activeTable() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setActive(true);
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        return table;
    }
}
