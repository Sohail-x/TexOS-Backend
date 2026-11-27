package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.KitchenAccessGrant;
import com.texos.texos_backend.entity.KitchenAccessStatus;
import com.texos.texos_backend.entity.LoyaltyAccount;
import com.texos.texos_backend.entity.MenuPriceTaxMode;
import com.texos.texos_backend.entity.OrderApprovalAccessCodeGrant;
import com.texos.texos_backend.entity.OrderApprovalCodeStatus;
import com.texos.texos_backend.entity.OrderApprovalPushSubscription;
import com.texos.texos_backend.entity.OrderApprovalSession;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.PasswordResetToken;
import com.texos.texos_backend.entity.PhonePeWebhookEvent;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.entity.RestaurantPaymentNotification;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.entity.Subscription;
import com.texos.texos_backend.entity.SubscriptionPayment;
import com.texos.texos_backend.entity.SubscriptionPlan;
import com.texos.texos_backend.entity.SubscriptionStatus;
import com.texos.texos_backend.entity.TrialClaim;
import com.texos.texos_backend.entity.WebhookEvent;
import com.texos.texos_backend.repository.DataRetentionRepository;
import com.texos.texos_backend.repository.PasswordResetTokenRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DataRetentionSchedulerIntegrationTest {

    @Autowired
    private DataRetentionScheduler scheduler;

    @Autowired
    private DataRetentionRepository dataRetentionRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void anonymizesOnlyDeletedAndInactiveProfilesPastTheirRetentionThresholds() {
        Instant now = Instant.now();
        Owner owner = createOwner("retention-owner");
        Owner recentDeletedOwner = createOwner("recent-owner");
        String recentDeletedOwnerEmail = recentDeletedOwner.getEmail();
        owner.setDeletedAt(now.minusSeconds(31L * 24 * 60 * 60));
        recentDeletedOwner.setDeletedAt(now.minusSeconds(29L * 24 * 60 * 60));
        owner.setActive(false);
        recentDeletedOwner.setActive(false);

        Restaurant restaurant = createRestaurant(owner);
        Customer deletedCustomer = createCustomer(restaurant, "deleted-customer");
        Customer recentDeletedCustomer = createCustomer(restaurant, "recent-customer");
        Customer inactiveCustomer = createCustomer(restaurant, "inactive-customer");
        Customer recentActivityCustomer = createCustomer(restaurant, "recent-activity");
        deletedCustomer.setActive(false);
        deletedCustomer.setDeletedAt(now.minusSeconds(31L * 24 * 60 * 60));
        recentDeletedCustomer.setActive(false);
        recentDeletedCustomer.setDeletedAt(now.minusSeconds(29L * 24 * 60 * 60));
        inactiveCustomer.setLastActivityAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(731));
        recentActivityCustomer.setLastActivityAt(
                LocalDateTime.now(ZoneOffset.UTC).minusDays(729));

        LoyaltyAccount account = new LoyaltyAccount();
        account.setCustomer(inactiveCustomer);
        account.setRestaurant(restaurant);
        account.setAvailablePoints(75L);
        account.setPointsDebt(10L);
        entityManager.persist(account);
        entityManager.flush();

        scheduler.anonymizeDeletedOwners(
                now.minusSeconds(30L * 24 * 60 * 60), now);
        scheduler.anonymizeDeletedCustomers(
                now.minusSeconds(30L * 24 * 60 * 60), now);
        scheduler.anonymizeInactiveCustomers(
                LocalDateTime.now(ZoneOffset.UTC).minusDays(730), now);
        entityManager.flush();
        Long ownerId = owner.getId();
        Long recentOwnerId = recentDeletedOwner.getId();
        Long deletedCustomerId = deletedCustomer.getId();
        Long recentDeletedCustomerId = recentDeletedCustomer.getId();
        Long inactiveCustomerId = inactiveCustomer.getId();
        Long recentActivityCustomerId = recentActivityCustomer.getId();
        Long accountId = account.getId();
        entityManager.clear();

        Owner anonymizedOwner = entityManager.find(Owner.class, ownerId);
        Owner retainedOwner = entityManager.find(Owner.class, recentOwnerId);
        Customer anonymizedDeletedCustomer =
                entityManager.find(Customer.class, deletedCustomerId);
        Customer retainedDeletedCustomer =
                entityManager.find(Customer.class, recentDeletedCustomerId);
        Customer anonymizedInactiveCustomer =
                entityManager.find(Customer.class, inactiveCustomerId);
        Customer retainedActiveCustomer =
                entityManager.find(Customer.class, recentActivityCustomerId);
        LoyaltyAccount forfeitedAccount = entityManager.find(LoyaltyAccount.class, accountId);

        assertEquals("Deleted User", anonymizedOwner.getFullName());
        assertEquals("deleted_" + ownerId + "@anonymized.local", anonymizedOwner.getEmail());
        assertNull(anonymizedOwner.getPasswordHash());
        assertNotNull(anonymizedOwner.getAnonymizedAt());
        assertNull(retainedOwner.getAnonymizedAt());
        assertEquals(recentDeletedOwnerEmail, retainedOwner.getEmail());

        assertEquals("Deleted User", anonymizedDeletedCustomer.getCustomerName());
        assertEquals("deleted_" + deletedCustomerId + "@anonymized.local",
                anonymizedDeletedCustomer.getEmail());
        assertNull(anonymizedDeletedCustomer.getMobileNumber());
        assertNull(anonymizedDeletedCustomer.getPasswordHash());
        assertNotNull(anonymizedDeletedCustomer.getAnonymizedAt());
        assertNull(retainedDeletedCustomer.getAnonymizedAt());
        assertEquals("recent-customer@example.test", retainedDeletedCustomer.getEmail());

        assertEquals("Deleted User", anonymizedInactiveCustomer.getCustomerName());
        assertFalse(anonymizedInactiveCustomer.isActive());
        assertNotNull(anonymizedInactiveCustomer.getAnonymizedAt());
        assertEquals(0L, forfeitedAccount.getAvailablePoints());
        assertEquals(0L, forfeitedAccount.getPointsDebt());
        assertTrue(retainedActiveCustomer.isActive());
        assertNull(retainedActiveCustomer.getAnonymizedAt());
    }

    @Test
    void archivesOnlyFinancialRowsOlderThanTheEightYearGuardrail() {
        Instant cutoffInstant = DataRetentionScheduler.indianFinancialYearCutoff(Instant.now());
        LocalDateTime cutoff = LocalDateTime.ofInstant(cutoffInstant, ZoneOffset.UTC);
        Owner owner = createOwner("financial-owner");
        Restaurant restaurant = createRestaurant(owner);
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber("T1");
        table.setQrToken(UUID.randomUUID().toString());
        entityManager.persist(table);

        Bill expiredBill = createBill(restaurant, table, "expired-bill");
        Bill retainedBill = createBill(restaurant, table, "retained-bill");
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("retention-plan-" + UUID.randomUUID());
        plan.setPrice(new BigDecimal("499.00"));
        plan.setDurationDays(30);
        entityManager.persist(plan);
        Subscription subscription = new Subscription();
        subscription.setRestaurant(restaurant);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        entityManager.persist(subscription);
        SubscriptionPayment expiredPayment =
                createSubscriptionPayment(restaurant, subscription, plan);
        SubscriptionPayment retainedPayment =
                createSubscriptionPayment(restaurant, subscription, plan);
        entityManager.flush();

        LocalDateTime expiredAt = cutoff.minusSeconds(1);
        LocalDateTime retainedAt = cutoff;
        entityManager.createNativeQuery("UPDATE bills SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", expiredAt)
                .setParameter("id", expiredBill.getId())
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE bills SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", retainedAt)
                .setParameter("id", retainedBill.getId())
                .executeUpdate();
        entityManager.createNativeQuery(
                        "UPDATE payments SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", expiredAt)
                .setParameter("id", expiredPayment.getId())
                .executeUpdate();
        entityManager.createNativeQuery(
                        "UPDATE payments SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", retainedAt)
                .setParameter("id", retainedPayment.getId())
                .executeUpdate();
        Long expiredBillId = expiredBill.getId();
        Long retainedBillId = retainedBill.getId();
        Long retainedOrderId = retainedBill.getOrder().getId();
        Long expiredPaymentId = expiredPayment.getId();
        Long retainedPaymentId = retainedPayment.getId();
        entityManager.clear();

        scheduler.archiveExpiredFinancialRecords(cutoff);
        entityManager.flush();
        entityManager.clear();

        assertNull(entityManager.find(Bill.class, expiredBillId));
        assertNotNull(entityManager.find(Bill.class, retainedBillId));
        assertEquals(new BigDecimal("125.00"),
                entityManager.find(RestaurantOrder.class, retainedOrderId).getTotalAmount());
        assertNull(entityManager.find(SubscriptionPayment.class, expiredPaymentId));
        assertNotNull(entityManager.find(SubscriptionPayment.class, retainedPaymentId));
    }

    @Test
    void removesExpiredSecurityAndProcessedEventRowsOnlyPastTheirRetentionCutoffs() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Owner owner = createOwner("cleanup-owner");
        Restaurant restaurant = createRestaurant(owner);

        PasswordResetToken expiredToken = createResetToken(owner, now.minusDays(8));
        PasswordResetToken recentToken = createResetToken(owner, now.minusDays(6));
        KitchenAccessGrant expiredKitchenGrant = createKitchenGrant(
                restaurant, KitchenAccessStatus.EXPIRED, now.minusDays(91));
        KitchenAccessGrant recentKitchenGrant = createKitchenGrant(
                restaurant, KitchenAccessStatus.EXPIRED, now.minusDays(89));
        OrderApprovalAccessCodeGrant expiredAccessCode = createAccessCode(
                restaurant, OrderApprovalCodeStatus.EXPIRED, now.minusDays(91));
        OrderApprovalAccessCodeGrant recentAccessCode = createAccessCode(
                restaurant, OrderApprovalCodeStatus.EXPIRED, now.minusDays(89));
        WebhookEvent oldWebhook = createWebhookEvent(now.minusDays(181));
        WebhookEvent recentWebhook = createWebhookEvent(now.minusDays(179));
        PhonePeWebhookEvent oldPhonePeWebhook =
                createPhonePeWebhookEvent(now.minusDays(181));
        PhonePeWebhookEvent recentPhonePeWebhook =
                createPhonePeWebhookEvent(now.minusDays(179));
        RestaurantPaymentNotification oldNotification =
                createNotification(restaurant, now.minusDays(181));
        RestaurantPaymentNotification recentNotification =
                createNotification(restaurant, now.minusDays(179));
        entityManager.flush();

        passwordResetTokenRepository.deleteLongExpiredTokens(now.minusDays(7));
        dataRetentionRepository.deleteOldInactiveKitchenGrants(now.minusDays(90));
        dataRetentionRepository.deleteOldInactiveOrderApprovalCodes(now.minusDays(90));
        dataRetentionRepository.deleteOldProcessedWebhookEvents(now.minusDays(180));
        dataRetentionRepository.deleteOldProcessedPhonePeWebhookEvents(now.minusDays(180));
        dataRetentionRepository.deleteOldClosedPaymentNotifications(now.minusDays(180));
        entityManager.clear();

        assertNull(entityManager.find(PasswordResetToken.class, expiredToken.getId()));
        assertNotNull(entityManager.find(PasswordResetToken.class, recentToken.getId()));
        assertNull(entityManager.find(KitchenAccessGrant.class, expiredKitchenGrant.getId()));
        assertNotNull(entityManager.find(KitchenAccessGrant.class, recentKitchenGrant.getId()));
        assertNull(entityManager.find(OrderApprovalAccessCodeGrant.class, expiredAccessCode.getId()));
        assertNotNull(entityManager.find(OrderApprovalAccessCodeGrant.class, recentAccessCode.getId()));
        assertNull(entityManager.find(WebhookEvent.class, oldWebhook.getId()));
        assertNotNull(entityManager.find(WebhookEvent.class, recentWebhook.getId()));
        assertNull(entityManager.find(PhonePeWebhookEvent.class, oldPhonePeWebhook.getId()));
        assertNotNull(entityManager.find(PhonePeWebhookEvent.class, recentPhonePeWebhook.getId()));
        assertNull(entityManager.find(RestaurantPaymentNotification.class, oldNotification.getId()));
        assertNotNull(entityManager.find(RestaurantPaymentNotification.class, recentNotification.getId()));
    }

    @Test
    void purgesRegistrationMetadataAfterTheDeletedOwnerHold() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Owner owner = createOwner("registration-owner");
        owner.setDeletedAt(Instant.now().minusSeconds(181L * 24 * 60 * 60));
        owner.setActive(false);
        Restaurant restaurant = createRestaurant(owner);
        restaurant.setDeletedAt(Instant.now().minusSeconds(181L * 24 * 60 * 60));

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("retention-trial-plan-" + UUID.randomUUID());
        plan.setPrice(BigDecimal.ZERO);
        plan.setDurationDays(30);
        plan.setTrialPlan(true);
        entityManager.persist(plan);
        Subscription subscription = new Subscription();
        subscription.setRestaurant(restaurant);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        entityManager.persist(subscription);

        TrialClaim trialClaim = new TrialClaim();
        trialClaim.setRestaurantId(restaurant.getId());
        trialClaim.setSubscriptionId(subscription.getId());
        entityManager.persist(trialClaim);
        PasswordResetToken resetToken = createResetToken(owner, now);
        KitchenAccessGrant kitchenGrant = createKitchenGrant(
                restaurant, KitchenAccessStatus.REVOKED, now.minusDays(181));
        OrderApprovalSession approvalSession = new OrderApprovalSession();
        approvalSession.setSessionId(UUID.randomUUID());
        approvalSession.setRestaurant(restaurant);
        approvalSession.setAccessCodeHash(UUID.randomUUID().toString());
        approvalSession.setExpiresAt(now.minusDays(1));
        entityManager.persist(approvalSession);
        OrderApprovalAccessCodeGrant accessCode = createAccessCode(
                restaurant, OrderApprovalCodeStatus.REVOKED, now.minusDays(181));
        accessCode.setSessionId(approvalSession.getSessionId());
        OrderApprovalPushSubscription pushSubscription =
                createPushSubscription(approvalSession);
        entityManager.flush();

        Long trialRestaurantId = trialClaim.getRestaurantId();
        Long resetTokenId = resetToken.getId();
        Long kitchenGrantId = kitchenGrant.getId();
        Long accessCodeId = accessCode.getId();
        Long pushSubscriptionId = pushSubscription.getId();
        UUID sessionId = approvalSession.getSessionId();

        int purged = dataRetentionRepository.purgeDeletedRestaurantRegistrationMetadata(
                now.minusDays(180));
        entityManager.clear();

        assertEquals(6, purged);
        assertNull(entityManager.find(TrialClaim.class, trialRestaurantId));
        assertNull(entityManager.find(PasswordResetToken.class, resetTokenId));
        assertNull(entityManager.find(KitchenAccessGrant.class, kitchenGrantId));
        assertNull(entityManager.find(OrderApprovalAccessCodeGrant.class, accessCodeId));
        assertNull(entityManager.find(OrderApprovalPushSubscription.class, pushSubscriptionId));
        assertNull(entityManager.find(OrderApprovalSession.class, sessionId));
    }

    private Owner createOwner(String prefix) {
        Owner owner = new Owner();
        owner.setFullName("Retention Test Owner");
        owner.setEmail(prefix + "-" + UUID.randomUUID() + "@example.test");
        owner.setPasswordHash("test-password-hash");
        entityManager.persist(owner);
        return owner;
    }

    private Restaurant createRestaurant(Owner owner) {
        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName("Retention Test Restaurant");
        restaurant.setOwner(owner);
        entityManager.persist(restaurant);
        return restaurant;
    }

    private Customer createCustomer(Restaurant restaurant, String prefix) {
        Customer customer = new Customer();
        customer.setRestaurant(restaurant);
        customer.setCustomerName(prefix);
        customer.setMobileNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        customer.setEmail(prefix + "@example.test");
        customer.setPasswordHash("test-password-hash");
        entityManager.persist(customer);
        return customer;
    }

    private Bill createBill(
            Restaurant restaurant,
            RestaurantTable table,
            String prefix) {
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setRestaurant(restaurant);
        order.setTable(table);
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
        return bill;
    }

    private SubscriptionPayment createSubscriptionPayment(
            Restaurant restaurant,
            Subscription subscription,
            SubscriptionPlan plan) {
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setRestaurant(restaurant);
        payment.setSubscription(subscription);
        payment.setPlan(plan);
        payment.setAmount(new BigDecimal("499.00"));
        payment.setCurrency("INR");
        payment.setProviderOrderId("retention-" + UUID.randomUUID());
        payment.setPaymentStatus("COMPLETED");
        payment.setProviderVerified(true);
        entityManager.persist(payment);
        return payment;
    }

    private PasswordResetToken createResetToken(Owner owner, LocalDateTime expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setOwnerId(owner.getId());
        token.setTokenHash(UUID.randomUUID().toString().replace("-", ""));
        token.setExpiresAt(expiresAt);
        entityManager.persist(token);
        return token;
    }

    private KitchenAccessGrant createKitchenGrant(
            Restaurant restaurant,
            KitchenAccessStatus status,
            LocalDateTime expiresAt) {
        KitchenAccessGrant grant = new KitchenAccessGrant();
        grant.setRestaurant(restaurant);
        grant.setTokenHash(UUID.randomUUID().toString().replace("-", ""));
        grant.setStatus(status);
        grant.setExpiresAt(expiresAt);
        if (status == KitchenAccessStatus.REVOKED) {
            grant.setRevokedAt(expiresAt);
        }
        entityManager.persist(grant);
        return grant;
    }

    private OrderApprovalAccessCodeGrant createAccessCode(
            Restaurant restaurant,
            OrderApprovalCodeStatus status,
            LocalDateTime expiresAt) {
        OrderApprovalAccessCodeGrant grant = new OrderApprovalAccessCodeGrant();
        grant.setRestaurant(restaurant);
        grant.setCodeHash(UUID.randomUUID().toString().replace("-", ""));
        grant.setStatus(status);
        grant.setExpiresAt(expiresAt);
        if (status == OrderApprovalCodeStatus.REVOKED) {
            grant.setRevokedAt(expiresAt);
        }
        entityManager.persist(grant);
        return grant;
    }

    private WebhookEvent createWebhookEvent(LocalDateTime processedAt) {
        WebhookEvent event = new WebhookEvent();
        event.setProviderEventId(UUID.randomUUID().toString());
        event.setEventType("SUBSCRIPTION_PAID");
        event.setProcessingStatus("PROCESSED");
        event.setProcessedAt(processedAt);
        entityManager.persist(event);
        return event;
    }

    private PhonePeWebhookEvent createPhonePeWebhookEvent(LocalDateTime processedAt) {
        PhonePeWebhookEvent event = new PhonePeWebhookEvent();
        event.setEventHash(UUID.randomUUID().toString().replace("-", ""));
        event.setMerchantOrderId(UUID.randomUUID().toString());
        event.setEventType("PAYMENT_SUCCESS");
        event.setPayloadJson("{}");
        event.setSignatureVerified(true);
        event.setProcessingStatus("PROCESSED");
        event.setProcessedAt(processedAt);
        entityManager.persist(event);
        return event;
    }

    private RestaurantPaymentNotification createNotification(
            Restaurant restaurant,
            LocalDateTime closedAt) {
        RestaurantPaymentNotification notification = new RestaurantPaymentNotification();
        notification.setRestaurant(restaurant);
        notification.setNotificationType("PAYMENT");
        notification.setPriority(1);
        notification.setNotificationStatus("DISMISSED");
        notification.setClosedAt(closedAt);
        entityManager.persist(notification);
        return notification;
    }

    private OrderApprovalPushSubscription createPushSubscription(
            OrderApprovalSession session) {
        OrderApprovalPushSubscription subscription = new OrderApprovalPushSubscription();
        subscription.setSession(session);
        subscription.setEndpointHash(UUID.randomUUID().toString().replace("-", ""));
        subscription.setEndpoint("https://example.test/" + UUID.randomUUID());
        subscription.setP256dh("public-key");
        subscription.setAuthKey("auth-key");
        entityManager.persist(subscription);
        return subscription;
    }
}
