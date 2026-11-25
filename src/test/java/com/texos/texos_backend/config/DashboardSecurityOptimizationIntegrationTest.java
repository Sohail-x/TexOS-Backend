package com.texos.texos_backend.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.Subscription;
import com.texos.texos_backend.entity.SubscriptionPlan;
import com.texos.texos_backend.entity.SubscriptionStatus;
import com.texos.texos_backend.service.JwtService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardSecurityOptimizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MeterRegistry meterRegistry;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void inactiveOwnerAndStaleTokenVersionAreUnauthorized() throws Exception {
        Owner inactiveOwner = createOwner(false, 2);
        Restaurant inactiveRestaurant = createRestaurant(inactiveOwner);
        SubscriptionPlan inactivePlan = createPlan();
        addSubscription(
                inactiveRestaurant, inactivePlan, SubscriptionStatus.ACTIVE,
                LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        String inactiveToken =
                jwtService.generateToken(inactiveOwner.getEmail(), inactiveOwner.getTokenVersion());
        expectStatus(inactiveRestaurant.getId(), inactiveToken, 401);

        Owner versionedOwner = createOwner(true, 5);
        Restaurant versionedRestaurant = createRestaurant(versionedOwner);
        SubscriptionPlan versionedPlan = createPlan();
        addSubscription(
                versionedRestaurant, versionedPlan, SubscriptionStatus.ACTIVE,
                LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        String staleToken = jwtService.generateToken(versionedOwner.getEmail(), 4);
        expectStatus(versionedRestaurant.getId(), staleToken, 401);
    }

    @Test
    void wrongRestaurantOwnershipIsForbidden() throws Exception {
        Owner authenticatedOwner = createOwner(true, 0);
        Owner restaurantOwner = createOwner(true, 0);
        Restaurant restaurant = createRestaurant(restaurantOwner);
        SubscriptionPlan plan = createPlan();
        addSubscription(
                restaurant, plan, SubscriptionStatus.ACTIVE,
                LocalDateTime.now(ZoneOffset.UTC).plusDays(2));

        String token = jwtService.generateToken(
                authenticatedOwner.getEmail(), authenticatedOwner.getTokenVersion());
        expectStatus(restaurant.getId(), token, 403);
    }

    @Test
    void expiredAndNullSubscriptionEndDatesAreForbidden() throws Exception {
        Owner expiredOwner = createOwner(true, 0);
        Restaurant expiredRestaurant = createRestaurant(expiredOwner);
        SubscriptionPlan expiredPlan = createPlan();
        addSubscription(
                expiredRestaurant, expiredPlan, SubscriptionStatus.ACTIVE,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        expectStatus(expiredRestaurant.getId(),
                jwtService.generateToken(expiredOwner.getEmail(), expiredOwner.getTokenVersion()), 403);

        Owner nullEndDateOwner = createOwner(true, 0);
        Restaurant nullEndDateRestaurant = createRestaurant(nullEndDateOwner);
        SubscriptionPlan nullEndDatePlan = createPlan();
        addSubscription(nullEndDateRestaurant, nullEndDatePlan, SubscriptionStatus.TRIALING, null);
        expectStatus(nullEndDateRestaurant.getId(),
                jwtService.generateToken(
                        nullEndDateOwner.getEmail(), nullEndDateOwner.getTokenVersion()),
                403);
    }

    @Test
    void multipleEligibleSubscriptionsUseTheLatestEndDate() throws Exception {
        Owner owner = createOwner(true, 0);
        Restaurant restaurant = createRestaurant(owner);
        SubscriptionPlan plan = createPlan();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        addSubscription(restaurant, plan, SubscriptionStatus.ACTIVE, now.minusDays(1));
        addSubscription(restaurant, plan, SubscriptionStatus.TRIALING, now.plusDays(2));

        String token = jwtService.generateToken(owner.getEmail(), owner.getTokenVersion());
        expectStatus(restaurant.getId(), token, 200);
    }

    @Test
    void notificationNextRecordsItsSecurityAndRepositoryStages() throws Exception {
        Owner owner = createOwner(true, 0);
        Restaurant restaurant = createRestaurant(owner);
        SubscriptionPlan plan = createPlan();
        addSubscription(
                restaurant, plan, SubscriptionStatus.ACTIVE,
                LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        String token = jwtService.generateToken(owner.getEmail(), owner.getTokenVersion());

        mockMvc.perform(get(
                        "/api/restaurants/{restaurantId}/payments/notifications/next",
                        restaurant.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));

        assertApiStageRecorded("jwt_owner_lookup");
        assertApiStageRecorded("restaurant_ownership_validation");
        assertApiStageRecorded("subscription_validation");
        assertApiStageRecorded("service_ownership_validation");
        assertApiStageRecorded("pending_notification_query");
    }

    private void assertApiStageRecorded(String stage) {
        var timer = meterRegistry.find("texos.api.stage.duration")
                .tag("endpoint_type", "payment_notification_next")
                .tag("stage", stage)
                .tag("status", "200")
                .tag("outcome", "completed")
                .timer();
        assertNotNull(timer, "Expected stage timer: " + stage);
        assertTrue(timer.count() > 0, "Expected a stage sample: " + stage);
    }

    private Owner createOwner(boolean active, int tokenVersion) {
        Owner owner = new Owner();
        String unique = UUID.randomUUID().toString();
        owner.setFullName("Security test owner");
        owner.setEmail("security-" + unique + "@example.test");
        owner.setPasswordHash("test-password-hash");
        owner.setActive(active);
        owner.setTokenVersion(tokenVersion);
        entityManager.persist(owner);
        entityManager.flush();
        return owner;
    }

    private Restaurant createRestaurant(Owner owner) {
        Restaurant restaurant = new Restaurant();
        restaurant.setRestaurantName("Security test restaurant " + UUID.randomUUID());
        restaurant.setOwner(owner);
        entityManager.persist(restaurant);
        entityManager.flush();
        return restaurant;
    }

    private SubscriptionPlan createPlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("Security test plan " + UUID.randomUUID());
        plan.setPrice(new BigDecimal("1.00"));
        plan.setCurrency("INR");
        plan.setDurationDays(30);
        plan.setTrialPlan(false);
        plan.setActive(true);
        entityManager.persist(plan);
        entityManager.flush();
        return plan;
    }

    private void addSubscription(
            Restaurant restaurant,
            SubscriptionPlan plan,
            SubscriptionStatus status,
            LocalDateTime endDate) {
        Subscription subscription = new Subscription();
        subscription.setRestaurant(restaurant);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setStartDate(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        subscription.setEndDate(endDate);
        entityManager.persist(subscription);
        entityManager.flush();
    }

    private void expectStatus(Long restaurantId, String token, int expectedStatus)
            throws Exception {
        mockMvc.perform(get("/api/restaurants/{restaurantId}/dashboard/summary", restaurantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }
}
