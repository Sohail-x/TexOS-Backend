package com.texos.texos_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.Subscription;
import com.texos.texos_backend.entity.SubscriptionPayment;
import com.texos.texos_backend.entity.SubscriptionPlan;
import com.texos.texos_backend.entity.SubscriptionStatus;
import com.texos.texos_backend.exception.ResourceConflictException;
import com.texos.texos_backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {
    @Test
    void subscriptionEligibilityReadsOnlyTheLatestScalarEndDate() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionService service = service(
                mock(SubscriptionPlanRepository.class),
                subscriptions,
                mock(SubscriptionPaymentRepository.class),
                mock(TrialClaimRepository.class),
                mock(RestaurantRepository.class),
                mock(WebhookEventRepository.class),
                mock(PhonePeGateway.class));
        LocalDateTime futureEndDate = LocalDateTime.now(ZoneOffset.UTC).plusDays(1);
        when(subscriptions.findEndDatesByRestaurantIdAndStatusIn(
                13L,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING),
                PageRequest.of(0, 1)))
                .thenReturn(List.of(futureEndDate));

        assertTrue(service.hasValidSubscription(13L));

        verify(subscriptions).findEndDatesByRestaurantIdAndStatusIn(
                13L,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING),
                PageRequest.of(0, 1));
        verify(subscriptions, never())
                .findFirstByRestaurantIdAndStatusInOrderByEndDateDesc(anyLong(), anyList());
    }

    @Test
    void subscriptionEligibilityRejectsExpiredAndNullEndDates() {
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionService service = service(
                mock(SubscriptionPlanRepository.class),
                subscriptions,
                mock(SubscriptionPaymentRepository.class),
                mock(TrialClaimRepository.class),
                mock(RestaurantRepository.class),
                mock(WebhookEventRepository.class),
                mock(PhonePeGateway.class));
        when(subscriptions.findEndDatesByRestaurantIdAndStatusIn(
                eq(13L), anyList(), any()))
                .thenReturn(List.of(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)));
        when(subscriptions.findEndDatesByRestaurantIdAndStatusIn(
                eq(14L), anyList(), any()))
                .thenReturn(Collections.singletonList(null));

        assertFalse(service.hasValidSubscription(13L));
        assertFalse(service.hasValidSubscription(14L));
    }

    @Test
    void trialCanOnlyBeClaimedOnceForRestaurant() {
        SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        TrialClaimRepository claims = mock(TrialClaimRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        SubscriptionService service = service(plans, subscriptions, mock(SubscriptionPaymentRepository.class),
                claims, restaurants, mock(WebhookEventRepository.class), mock(PhonePeGateway.class));
        Restaurant restaurant = new Restaurant();
        restaurant.setId(13L);
        SubscriptionPlan trial = plan(4L, "0.00", 30);
        when(restaurants.findOwnedForUpdate(13L, 8L)).thenReturn(Optional.of(restaurant));
        when(claims.existsById(13L)).thenReturn(false);
        when(plans.findFirstByTrialPlanTrueAndActiveTrue()).thenReturn(Optional.of(trial));
        when(subscriptions.save(any())).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setId(22L);
            return subscription;
        });

        var result = service.startTrial(13L, 8L);

        assertEquals("TRIALING", result.get("status"));
        assertEquals(30L, java.time.Duration.between(
                (LocalDateTime) result.get("startDate"), (LocalDateTime) result.get("endDate")).toDays());
        verify(claims).save(any());
        when(claims.existsById(13L)).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> service.startTrial(13L, 8L));
        verify(subscriptions, times(1)).save(any());
    }

    @Test
    void advanceRenewalExtendsCurrentExpiryAndExpiredRepurchaseStartsAtConfirmation() {
        LocalDateTime confirmed = LocalDateTime.of(2026, 11, 15, 10, 0);
        LocalDateTime expiry = confirmed.plusDays(5);
        assertEquals(expiry.plusDays(30),
                SubscriptionService.calculateEndDate(true, expiry, 30, confirmed));
        assertEquals(confirmed.plusDays(30),
                SubscriptionService.calculateEndDate(true, confirmed.minusSeconds(1), 30, confirmed));
        assertEquals(confirmed.plusDays(30),
                SubscriptionService.calculateEndDate(false, expiry, 30, confirmed));
    }

    @Test
    void createsPhonePeCheckoutUsingStoredSubscriptionPlanAmount() {
        SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionPaymentRepository payments = mock(SubscriptionPaymentRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        PhonePeGateway gateway = mock(PhonePeGateway.class);
        SubscriptionService service = service(plans, subscriptions, payments,
                mock(TrialClaimRepository.class), restaurants, mock(WebhookEventRepository.class), gateway);
        setSubscriptionCredentials(service);

        Restaurant restaurant = new Restaurant();
        restaurant.setId(13L);
        SubscriptionPlan plan = plan(6L, "125.50", 30);
        when(restaurants.findByIdAndOwnerId(13L, 8L)).thenReturn(Optional.of(restaurant));
        when(plans.findByIdAndActiveTrue(6L)).thenReturn(Optional.of(plan));
        when(payments.findFirstByRestaurant_IdAndPaymentStatusInOrderByCreatedAtDesc(
                eq(13L), anyCollection())).thenReturn(Optional.empty());
        when(subscriptions.findFirstByRestaurantIdAndStatusInOrderByEndDateDesc(eq(13L), any()))
                .thenReturn(Optional.empty());
        when(subscriptions.save(any())).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setId(9L);
            return subscription;
        });
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
            SubscriptionPayment payment = invocation.getArgument(0);
            payment.setId(14L);
            return payment;
        });
        when(gateway.createPayment(
                eq("texos-subscriptions"), any(), anyString(), eq(12550L), anyString()))
                .thenReturn(new PhonePeGateway.CreatedPayment("OMO123", "https://phonepe.test/pay", "PENDING"));

        var result = service.createOrder(13L, 6L, 8L);

        assertEquals(12550L, result.get("amount"));
        assertEquals("https://phonepe.test/pay", result.get("checkoutUrl"));
        assertTrue(((String) result.get("merchantOrderId")).startsWith("TXOS-SUB-13-"));
        verify(gateway).createPayment(eq("texos-subscriptions"), any(), anyString(), eq(12550L), anyString());
        verify(payments).save(argThat(payment -> payment.getAmount().equals(new BigDecimal("125.50"))
                && "PENDING".equals(payment.getPaymentStatus())
                && "OMO123".equals(payment.getProviderOrderId())));
    }

    @Test
    void subscriptionActivatesOnlyAfterPhonePeStatusConfirmsCompletedAmount() {
        SubscriptionPaymentRepository payments = mock(SubscriptionPaymentRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        PhonePeGateway gateway = mock(PhonePeGateway.class);
        SubscriptionService service = service(mock(SubscriptionPlanRepository.class), subscriptions, payments,
                mock(TrialClaimRepository.class), restaurants, mock(WebhookEventRepository.class), gateway);
        setSubscriptionCredentials(service);

        Restaurant restaurant = new Restaurant();
        restaurant.setId(13L);
        SubscriptionPlan plan = plan(6L, "499.00", 30);
        Subscription subscription = new Subscription();
        subscription.setId(9L);
        subscription.setRestaurant(restaurant);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING);
        SubscriptionPayment payment = payment(restaurant, subscription, plan, "TXOS-SUB-13-1");
        when(restaurants.findByIdAndOwnerId(13L, 8L)).thenReturn(Optional.of(restaurant));
        when(payments.findByMerchantOrderIdForUpdate(payment.getMerchantOrderId()))
                .thenReturn(Optional.of(payment));
        when(gateway.getOrderStatus(eq("texos-subscriptions"), any(), eq(payment.getMerchantOrderId()), eq(49900L)))
                .thenReturn(new PhonePeGateway.VerifiedPayment(
                        "COMPLETED", 49900L, "texos-merchant", "OMO123",
                        "UPI_INTENT", "phonepe-ref-1", LocalDateTime.now(), null));

        var result = service.verifyPayment(13L, 8L, payment.getMerchantOrderId());

        assertEquals("SUCCESSFUL", result.get("status"));
        assertEquals("CAPTURED", payment.getPaymentStatus());
        assertTrue(payment.isProviderVerified());
        assertEquals("ACTIVE", subscription.getStatus().name());
        verify(gateway).getOrderStatus(eq("texos-subscriptions"), any(), eq(payment.getMerchantOrderId()), eq(49900L));
    }

    @Test
    void pendingPhonePeStatusDoesNotActivateSubscription() {
        SubscriptionPaymentRepository payments = mock(SubscriptionPaymentRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        PhonePeGateway gateway = mock(PhonePeGateway.class);
        SubscriptionService service = service(mock(SubscriptionPlanRepository.class),
                mock(SubscriptionRepository.class), payments, mock(TrialClaimRepository.class),
                restaurants, mock(WebhookEventRepository.class), gateway);
        setSubscriptionCredentials(service);

        Restaurant restaurant = new Restaurant();
        restaurant.setId(13L);
        SubscriptionPlan plan = plan(6L, "499.00", 30);
        Subscription subscription = new Subscription();
        subscription.setId(9L);
        subscription.setRestaurant(restaurant);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING);
        SubscriptionPayment payment = payment(restaurant, subscription, plan, "TXOS-SUB-13-2");
        when(restaurants.findByIdAndOwnerId(13L, 8L)).thenReturn(Optional.of(restaurant));
        when(payments.findByMerchantOrderIdForUpdate(payment.getMerchantOrderId()))
                .thenReturn(Optional.of(payment));
        when(gateway.getOrderStatus(eq("texos-subscriptions"), any(), eq(payment.getMerchantOrderId()), eq(49900L)))
                .thenReturn(new PhonePeGateway.VerifiedPayment(
                        "PENDING", 49900L, "", "", null, null, null, null));

        var result = service.verifyPayment(13L, 8L, payment.getMerchantOrderId());

        assertEquals("PENDING", result.get("status"));
        assertEquals(SubscriptionStatus.PENDING, subscription.getStatus());
        assertEquals("PENDING", payment.getPaymentStatus());
    }

    @Test
    void unknownSubscriptionPaymentPreventsCreatingAnotherCheckout() {
        SubscriptionPaymentRepository payments = mock(SubscriptionPaymentRepository.class);
        RestaurantRepository restaurants = mock(RestaurantRepository.class);
        SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
        PhonePeGateway gateway = mock(PhonePeGateway.class);
        SubscriptionService service = service(plans, mock(SubscriptionRepository.class), payments,
                mock(TrialClaimRepository.class), restaurants, mock(WebhookEventRepository.class), gateway);
        setSubscriptionCredentials(service);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(13L);
        SubscriptionPlan plan = plan(6L, "125.50", 30);
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        SubscriptionPayment payment = payment(restaurant, subscription, plan, "TXOS-SUB-13-3");
        payment.setPaymentStatus("UNKNOWN");
        payment.setCheckoutUrl("");
        when(restaurants.findByIdAndOwnerId(13L, 8L)).thenReturn(Optional.of(restaurant));
        when(plans.findByIdAndActiveTrue(6L)).thenReturn(Optional.of(plan));
        when(payments.findFirstByRestaurant_IdAndPaymentStatusInOrderByCreatedAtDesc(
                eq(13L), anyCollection())).thenReturn(Optional.of(payment));

        var result = service.createOrder(13L, 6L, 8L);

        assertEquals(payment.getMerchantOrderId(), result.get("merchantOrderId"));
        assertEquals("UNKNOWN", result.get("status"));
        verify(gateway, never()).createPayment(anyString(), any(), anyString(), anyLong(), anyString());
    }

    private SubscriptionService service(
            SubscriptionPlanRepository plans,
            SubscriptionRepository subscriptions,
            SubscriptionPaymentRepository payments,
            TrialClaimRepository claims,
            RestaurantRepository restaurants,
            WebhookEventRepository events,
            PhonePeGateway gateway) {
        return new SubscriptionService(
                plans, subscriptions, payments, claims, restaurants, events, new ObjectMapper(), gateway);
    }

    private SubscriptionPlan plan(Long id, String price, int duration) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setName("Growth");
        plan.setPrice(new BigDecimal(price));
        plan.setCurrency("INR");
        plan.setDurationDays(duration);
        return plan;
    }

    private SubscriptionPayment payment(
            Restaurant restaurant, Subscription subscription, SubscriptionPlan plan, String merchantOrderId) {
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setId(14L);
        payment.setRestaurant(restaurant);
        payment.setSubscription(subscription);
        payment.setPlan(plan);
        payment.setAmount(plan.getPrice());
        payment.setCurrency(plan.getCurrency());
        payment.setPaymentStatus("PENDING");
        payment.setMerchantOrderId(merchantOrderId);
        payment.setProviderOrderId("OMO123");
        return payment;
    }

    private void setSubscriptionCredentials(SubscriptionService service) {
        ReflectionTestUtils.setField(service, "frontendOrigin", "https://texos.test");
        ReflectionTestUtils.setField(service, "phonePeClientId", "client-id");
        ReflectionTestUtils.setField(service, "phonePeClientSecret", "client-secret");
        ReflectionTestUtils.setField(service, "phonePeClientVersion", 1);
        ReflectionTestUtils.setField(service, "phonePeMerchantId", "texos-merchant");
        ReflectionTestUtils.setField(service, "phonePeEnvironment", "SANDBOX");
    }
}
