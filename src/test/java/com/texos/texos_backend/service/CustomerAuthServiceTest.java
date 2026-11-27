package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.CustomerLoginRequest;
import com.texos.texos_backend.dto.CustomerRegisterRequest;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.CustomerAccountIdentity;
import com.texos.texos_backend.entity.LoyaltyAccount;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.repository.CustomerAccountIdentityRepository;
import com.texos.texos_backend.repository.CustomerPaymentTransactionRepository;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.LoyaltyAccountRepository;
import com.texos.texos_backend.repository.LoyaltyPointLotRepository;
import com.texos.texos_backend.repository.OfflineAssistanceRequestRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import java.util.Optional;
import java.util.List;
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
    private final LoyaltyAccountRepository loyaltyAccountRepository =
            mock(LoyaltyAccountRepository.class);
    private final LoyaltyPointLotRepository loyaltyPointLotRepository =
            mock(LoyaltyPointLotRepository.class);
    private final RestaurantOrderRepository orderRepository =
            mock(RestaurantOrderRepository.class);
    private final CustomerPaymentTransactionRepository paymentTransactionRepository =
            mock(CustomerPaymentTransactionRepository.class);
    private final OfflineAssistanceRequestRepository assistanceRequestRepository =
            mock(OfflineAssistanceRequestRepository.class);
    private final CustomerAuthService service = new CustomerAuthService(
            customerRepository,
            identityRepository,
            tableRepository,
            passwordEncoder,
            loyaltyAccountRepository,
            loyaltyPointLotRepository,
            orderRepository,
            paymentTransactionRepository,
            assistanceRequestRepository);

    @Test
    void loginReportsWhenCustomerAccountDoesNotExist() {
        when(tableRepository.findByQrTokenAndActiveTrue(QR_TOKEN))
                .thenReturn(Optional.of(activeTable()));
        when(customerRepository.findByRestaurant_IdAndMobileNumberAndActiveTrueAndDeletedAtIsNull(
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
        when(customerRepository.findByRestaurant_IdAndMobileNumberAndActiveTrueAndDeletedAtIsNull(
                RESTAURANT_ID, MOBILE_NUMBER)).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        var response = service.login(loginRequest());

        assertEquals("Asha", response.getCustomerName());
        assertEquals(RESTAURANT_ID, response.getRestaurantId());
    }

    @Test
    void loginCreatesRestaurantScopedProfileForExistingGlobalCustomer() {
        Restaurant targetRestaurant = new Restaurant();
        targetRestaurant.setId(RESTAURANT_ID);
        targetRestaurant.setActive(true);
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(targetRestaurant);
        when(tableRepository.findByQrTokenAndActiveTrue(QR_TOKEN))
                .thenReturn(Optional.of(table));

        Restaurant originalRestaurant = new Restaurant();
        originalRestaurant.setId(99L);
        originalRestaurant.setActive(false);
        Customer originalCustomer = new Customer();
        originalCustomer.setId(21L);
        originalCustomer.setCustomerName("Asha");
        originalCustomer.setMobileNumber(MOBILE_NUMBER);
        originalCustomer.setEmail("asha@example.test");
        originalCustomer.setPasswordHash("encoded-password");
        originalCustomer.setRestaurant(originalRestaurant);
        CustomerAccountIdentity identity = new CustomerAccountIdentity();
        identity.setIdentityType("MOBILE");
        identity.setIdentityValue(MOBILE_NUMBER);
        identity.setCustomer(originalCustomer);

        when(customerRepository.findByRestaurant_IdAndMobileNumberAndActiveTrueAndDeletedAtIsNull(
                RESTAURANT_ID, MOBILE_NUMBER)).thenReturn(Optional.empty());
        when(customerRepository.findByRestaurant_IdAndMobileNumber(RESTAURANT_ID, MOBILE_NUMBER))
                .thenReturn(Optional.empty());
        when(identityRepository.findByIdentityTypeAndIdentityValue("MOBILE", MOBILE_NUMBER))
                .thenReturn(Optional.of(identity));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(34L);
            return customer;
        });
        when(identityRepository.findAllByCustomer_Id(21L)).thenReturn(List.of(identity));

        var response = service.login(loginRequest());

        assertEquals(34L, response.getCustomerId());
        assertEquals(RESTAURANT_ID, response.getRestaurantId());
        assertEquals("Asha", response.getCustomerName());
        assertEquals(RESTAURANT_ID, identity.getCustomer().getRestaurant().getId());
        verify(identityRepository).saveAll(List.of(identity));
    }

    @Test
    void crossRestaurantLoginDoesNotProvisionProfileWithInvalidPassword() {
        Restaurant targetRestaurant = new Restaurant();
        targetRestaurant.setId(RESTAURANT_ID);
        targetRestaurant.setActive(true);
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(targetRestaurant);
        when(tableRepository.findByQrTokenAndActiveTrue(QR_TOKEN))
                .thenReturn(Optional.of(table));

        Restaurant originalRestaurant = new Restaurant();
        originalRestaurant.setId(99L);
        Customer originalCustomer = new Customer();
        originalCustomer.setId(21L);
        originalCustomer.setPasswordHash("encoded-password");
        originalCustomer.setRestaurant(originalRestaurant);
        CustomerAccountIdentity identity = new CustomerAccountIdentity();
        identity.setCustomer(originalCustomer);
        when(customerRepository.findByRestaurant_IdAndMobileNumberAndActiveTrueAndDeletedAtIsNull(
                RESTAURANT_ID, MOBILE_NUMBER)).thenReturn(Optional.empty());
        when(customerRepository.findByRestaurant_IdAndMobileNumber(RESTAURANT_ID, MOBILE_NUMBER))
                .thenReturn(Optional.empty());
        when(identityRepository.findByIdentityTypeAndIdentityValue("MOBILE", MOBILE_NUMBER))
                .thenReturn(Optional.of(identity));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.login(loginRequest()));

        verify(customerRepository, org.mockito.Mockito.never()).save(any(Customer.class));
    }

    @Test
    void deletionRevokesCustomerAccessAndSchedulesProfileAnonymization() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        Customer customer = new Customer();
        customer.setId(12L);
        customer.setCustomerName("Asha");
        customer.setMobileNumber(MOBILE_NUMBER);
        customer.setEmail("asha@example.test");
        customer.setPasswordHash("old-password-hash");
        customer.setRestaurant(restaurant);
        LoyaltyAccount loyaltyAccount = new LoyaltyAccount();
        loyaltyAccount.setAvailablePoints(80L);
        loyaltyAccount.setPointsDebt(10L);
        when(customerRepository.findByIdAndRestaurant_IdForUpdate(12L, RESTAURANT_ID))
                .thenReturn(Optional.of(customer));
        when(loyaltyAccountRepository.findByRestaurant_IdAndCustomer_Id(RESTAURANT_ID, 12L))
                .thenReturn(Optional.of(loyaltyAccount));
        service.deleteAccount(12L, RESTAURANT_ID);

        assertFalse(customer.isActive());
        assertEquals("Asha", customer.getCustomerName());
        assertNull(customer.getMobileNumber());
        assertEquals("asha@example.test", customer.getEmail());
        assertNull(customer.getPasswordHash());
        assertNull(customer.getAnonymizedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(customer.getDeletedAt());
        assertEquals(0L, loyaltyAccount.getAvailablePoints());
        assertEquals(0L, loyaltyAccount.getPointsDebt());
        verify(identityRepository).deleteAllByCustomer_Id(12L);
        verify(orderRepository).anonymizeCustomerDetails(12L);
        verify(paymentTransactionRepository).anonymizeCustomerDetails(12L);
        verify(assistanceRequestRepository).anonymizeCustomerDetails(12L);
        verify(loyaltyPointLotRepository).forfeitAvailablePoints(12L);
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
