package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.texos.texos_backend.dto.CustomerLoginRequest;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.CustomerAccountIdentity;
import com.texos.texos_backend.entity.CustomerPaymentTransaction;
import com.texos.texos_backend.entity.LoyaltyAccount;
import com.texos.texos_backend.entity.LoyaltyPointLot;
import com.texos.texos_backend.entity.MenuPriceTaxMode;
import com.texos.texos_backend.entity.OfflineAssistanceRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantTable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CustomerAccountDeletionIntegrationTest {

    @Autowired
    private CustomerAuthService customerAuthService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void existingCustomerCanSignInAtAnotherRestaurantWithRestaurantScopedProfile() {
        Owner originalOwner = createOwner("cross-restaurant-original-owner");
        Restaurant originalRestaurant = new Restaurant();
        originalRestaurant.setRestaurantName("Inactive original restaurant");
        originalRestaurant.setOwner(originalOwner);
        originalRestaurant.setActive(false);
        entityManager.persist(originalRestaurant);

        Owner targetOwner = createOwner("cross-restaurant-target-owner");
        Restaurant targetRestaurant = new Restaurant();
        targetRestaurant.setRestaurantName("Active target restaurant");
        targetRestaurant.setOwner(targetOwner);
        entityManager.persist(targetRestaurant);

        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T1");
        table.setQrToken(UUID.randomUUID().toString());
        table.setRestaurant(targetRestaurant);
        entityManager.persist(table);

        String mobile = String.valueOf(
                9_000_000_000L
                        + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L));
        String passwordHash = passwordEncoder.encode("customer-password");
        Customer originalCustomer = new Customer();
        originalCustomer.setCustomerName("Customer Across Restaurants");
        originalCustomer.setMobileNumber(mobile);
        originalCustomer.setEmail("cross-restaurant-" + UUID.randomUUID() + "@example.test");
        originalCustomer.setPasswordHash(passwordHash);
        originalCustomer.setRestaurant(originalRestaurant);
        entityManager.persist(originalCustomer);

        CustomerAccountIdentity mobileIdentity = new CustomerAccountIdentity();
        mobileIdentity.setIdentityType("MOBILE");
        mobileIdentity.setIdentityValue(mobile);
        mobileIdentity.setCustomer(originalCustomer);
        entityManager.persist(mobileIdentity);
        CustomerAccountIdentity emailIdentity = new CustomerAccountIdentity();
        emailIdentity.setIdentityType("EMAIL");
        emailIdentity.setIdentityValue(originalCustomer.getEmail());
        emailIdentity.setCustomer(originalCustomer);
        entityManager.persist(emailIdentity);

        Long originalCustomerId = originalCustomer.getId();
        Long targetRestaurantId = targetRestaurant.getId();
        String qrToken = table.getQrToken();
        entityManager.flush();
        entityManager.clear();

        CustomerLoginRequest request = new CustomerLoginRequest();
        request.setMobileNumber(mobile);
        request.setPassword("customer-password");
        request.setQrToken(qrToken);
        var response = customerAuthService.login(request);

        entityManager.flush();
        entityManager.clear();

        assertEquals(targetRestaurantId, response.getRestaurantId());
        assertNotEquals(originalCustomerId, response.getCustomerId());
        Customer targetCustomer = entityManager.createQuery(
                        "select customer from Customer customer "
                                + "where customer.restaurant.id = :restaurantId "
                                + "and customer.mobileNumber = :mobile",
                        Customer.class)
                .setParameter("restaurantId", targetRestaurantId)
                .setParameter("mobile", mobile)
                .getSingleResult();
        assertEquals(originalCustomer.getCustomerName(), targetCustomer.getCustomerName());
        assertTrue(passwordEncoder.matches("customer-password", targetCustomer.getPasswordHash()));
        List<CustomerAccountIdentity> identities = entityManager.createQuery(
                        "select identity from CustomerAccountIdentity identity "
                                + "where identity.customer.id = :customerId",
                        CustomerAccountIdentity.class)
                .setParameter("customerId", targetCustomer.getId())
                .getResultList();
        assertEquals(2, identities.size());
        assertNotNull(entityManager.find(Customer.class, originalCustomerId));
    }

    @Test
    void revokesCustomerAccessForfeitsPointsAndKeepsInvoiceAmountsUntilAnonymization() {
        Owner owner = new Owner();
        owner.setFullName("Account deletion test owner");
        owner.setEmail("account-deletion-" + UUID.randomUUID() + "@example.test");
        owner.setPasswordHash("test-password-hash");
        entityManager.persist(owner);

        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName("Account deletion test restaurant");
        restaurant.setOwner(owner);
        entityManager.persist(restaurant);

        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T1");
        table.setQrToken(UUID.randomUUID().toString());
        table.setRestaurant(restaurant);
        entityManager.persist(table);

        Customer customer = new Customer();
        customer.setCustomerName("Customer PII");
        customer.setMobileNumber("9876543210");
        customer.setEmail("customer-pii@example.test");
        customer.setPasswordHash("old-password-hash");
        customer.setRestaurant(restaurant);
        entityManager.persist(customer);

        CustomerAccountIdentity identity = new CustomerAccountIdentity();
        identity.setIdentityType("MOBILE");
        identity.setIdentityValue("identity-" + UUID.randomUUID());
        identity.setCustomer(customer);
        entityManager.persist(identity);

        RestaurantOrder order = new RestaurantOrder();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setCustomer(customer);
        order.setCustomerName("Customer PII");
        order.setCustomerMobile("9876543210");
        order.setCustomerEmail("customer-pii@example.test");
        order.setTotalAmount(new BigDecimal("125.00"));
        entityManager.persist(order);

        Bill bill = new Bill();
        bill.setOrder(order);
        bill.setSubtotal(new BigDecimal("100.00"));
        bill.setTaxMode(MenuPriceTaxMode.EXCLUSIVE);
        bill.setTaxRate(new BigDecimal("10.00"));
        bill.setTaxAmount(new BigDecimal("10.00"));
        bill.setServiceChargeEnabled(false);
        bill.setServiceChargeRate(BigDecimal.ZERO);
        bill.setServiceChargeAmount(BigDecimal.ZERO);
        bill.setTotalAmount(new BigDecimal("125.00"));
        entityManager.persist(bill);

        LoyaltyAccount loyaltyAccount = new LoyaltyAccount();
        loyaltyAccount.setRestaurant(restaurant);
        loyaltyAccount.setCustomer(customer);
        loyaltyAccount.setAvailablePoints(45L);
        loyaltyAccount.setPointsDebt(5L);
        entityManager.persist(loyaltyAccount);

        LoyaltyPointLot pointLot = new LoyaltyPointLot();
        pointLot.setAccount(loyaltyAccount);
        pointLot.setRestaurant(restaurant);
        pointLot.setCustomer(customer);
        pointLot.setEarnedPoints(45L);
        pointLot.setAvailablePoints(45L);
        entityManager.persist(pointLot);

        CustomerPaymentTransaction payment = new CustomerPaymentTransaction();
        payment.setRestaurant(restaurant);
        payment.setTable(table);
        payment.setCustomer(customer);
        payment.setBillKey("bill-key");
        payment.setAmount(new BigDecimal("125.00"));
        payment.setPaymentStatus("PENDING");
        entityManager.persist(payment);

        OfflineAssistanceRequest assistance = new OfflineAssistanceRequest();
        assistance.setRestaurant(restaurant);
        assistance.setTable(table);
        assistance.setLatestOrder(order);
        assistance.setCustomer(customer);
        assistance.setCustomerName("Customer PII");
        assistance.setCustomerMobile("9876543210");
        assistance.setBillKey("bill-key");
        assistance.setRequestStatus("PENDING");
        entityManager.persist(assistance);
        entityManager.flush();
        Long customerId = customer.getId();
        Long orderId = order.getId();
        Long billId = bill.getId();
        Long accountId = loyaltyAccount.getId();
        Long pointLotId = pointLot.getId();
        Long paymentId = payment.getId();
        Long assistanceId = assistance.getId();

        customerAuthService.deleteAccount(customerId, restaurant.getId());
        entityManager.flush();
        entityManager.clear();

        Customer deletedCustomer = entityManager.find(Customer.class, customerId);
        RestaurantOrder anonymizedOrder = entityManager.find(RestaurantOrder.class, orderId);
        Bill retainedBill = entityManager.find(Bill.class, billId);
        LoyaltyAccount forfeitedAccount = entityManager.find(LoyaltyAccount.class, accountId);
        LoyaltyPointLot forfeitedLot = entityManager.find(LoyaltyPointLot.class, pointLotId);
        CustomerPaymentTransaction retainedPayment =
                entityManager.find(CustomerPaymentTransaction.class, paymentId);
        OfflineAssistanceRequest anonymizedAssistance =
                entityManager.find(OfflineAssistanceRequest.class, assistanceId);

        assertFalse(deletedCustomer.isActive());
        assertEquals("Customer PII", deletedCustomer.getCustomerName());
        assertNull(deletedCustomer.getMobileNumber());
        assertEquals("customer-pii@example.test", deletedCustomer.getEmail());
        assertNull(deletedCustomer.getPasswordHash());
        assertNull(deletedCustomer.getAnonymizedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(deletedCustomer.getDeletedAt());
        assertNull(anonymizedOrder.getCustomer());
        assertNull(anonymizedOrder.getCustomerName());
        assertNull(anonymizedOrder.getCustomerMobile());
        assertNull(anonymizedOrder.getCustomerEmail());
        assertEquals(new BigDecimal("125.00"), anonymizedOrder.getTotalAmount());
        assertEquals(new BigDecimal("125.00"), retainedBill.getTotalAmount());
        assertEquals(0L, forfeitedAccount.getAvailablePoints());
        assertEquals(0L, forfeitedAccount.getPointsDebt());
        assertEquals(0L, forfeitedLot.getAvailablePoints());
        assertNull(retainedPayment.getCustomer());
        assertNull(anonymizedAssistance.getCustomer());
        assertNull(anonymizedAssistance.getCustomerName());
        assertNull(anonymizedAssistance.getCustomerMobile());
        assertEquals(0L, entityManager.createQuery(
                        "select count(accountIdentity) from CustomerAccountIdentity accountIdentity "
                                + "where accountIdentity.customer.id = :customerId", Long.class)
                .setParameter("customerId", customerId)
                .getSingleResult());
    }

    private Owner createOwner(String prefix) {
        Owner owner = new Owner();
        owner.setFullName(prefix);
        owner.setEmail(prefix + "-" + UUID.randomUUID() + "@example.test");
        owner.setPasswordHash("test-password-hash");
        entityManager.persist(owner);
        return owner;
    }
}
