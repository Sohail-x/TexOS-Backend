package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.Subscription;
import com.texos.texos_backend.entity.SubscriptionPlan;
import com.texos.texos_backend.entity.SubscriptionStatus;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.SubscriptionPlanRepository;
import com.texos.texos_backend.repository.SubscriptionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {
    private final RestaurantRepository restaurantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRestaurants() {
        List<Subscription> allSubscriptions = subscriptionRepository.findAll(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Subscription> latestByRestaurant = new LinkedHashMap<>();
        for (Subscription subscription : allSubscriptions) {
            latestByRestaurant.putIfAbsent(subscription.getRestaurant().getId(), subscription);
        }

        LocalDateTime now = utcNow();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Restaurant restaurant : restaurantRepository.findAllByActiveTrueOrderByRestaurantNameAsc()) {
            Subscription subscription = latestByRestaurant.get(restaurant.getId());
            boolean entitled = subscription != null
                    && (subscription.getStatus() == SubscriptionStatus.ACTIVE
                    || subscription.getStatus() == SubscriptionStatus.TRIALING)
                    && subscription.getEndDate() != null
                    && subscription.getEndDate().isAfter(now);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("restaurantId", restaurant.getId());
            row.put("restaurantName", restaurant.getRestaurantName());
            row.put("ownerName", restaurant.getOwner().getFullName());
            row.put("ownerEmail", restaurant.getOwner().getEmail());
            row.put("ownerPhone", restaurant.getOwner().getPhone());
            row.put("subscriptionStatus", subscription != null && entitled ? subscription.getStatus().name()
                    : subscription == null ? "NONE" : "INACTIVE");
            row.put("subscriptionActive", entitled);
            row.put("planName", subscription == null ? "" : subscription.getPlan().getName());
            row.put("expiresAt", subscription == null ? null : subscription.getEndDate());
            row.put("daysUntilExpiry", entitled && subscription != null && subscription.getEndDate() != null
                    ? Math.max(0, (long) Math.ceil(Duration.between(now, subscription.getEndDate()).toSeconds() / 86400.0))
                    : null);
            result.add(row);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> grantSubscription(Long restaurantId, Long planId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .filter(foundRestaurant -> foundRestaurant != null && foundRestaurant.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Active restaurant not found"));
        SubscriptionPlan plan = planRepository.findByIdAndActiveTrue(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Active subscription plan not found"));
        if (plan.isTrialPlan() || plan.getDurationDays() < 1) {
            throw new InvalidRequestException("Admin grants require an active paid subscription plan");
        }

        LocalDateTime now = utcNow();
        Subscription current = subscriptionRepository
                .findFirstByRestaurantIdAndStatusInOrderByEndDateDesc(
                        restaurantId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                .filter(s -> s.getEndDate() != null && s.getEndDate().isAfter(now))
                .orElse(null);
        Subscription granted = new Subscription();
        granted.setRestaurant(restaurant);
        granted.setPlan(plan);
        granted.setStatus(SubscriptionStatus.ACTIVE);
        granted.setStartDate(now);
        granted.setEndDate((current == null ? now : current.getEndDate()).plusDays(plan.getDurationDays()));
        return subscriptionView(subscriptionRepository.save(granted), restaurant);
    }

    @Transactional
    public Map<String, Object> cancelSubscription(Long restaurantId) {
        LocalDateTime now = utcNow();
        Subscription current = subscriptionRepository
                .findFirstByRestaurantIdAndStatusInOrderByEndDateDesc(
                        restaurantId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING))
                .filter(s -> s.getEndDate() != null && s.getEndDate().isAfter(now))
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription to cancel"));
        current.setStatus(SubscriptionStatus.CANCELLED);
        return subscriptionView(subscriptionRepository.save(current), current.getRestaurant());
    }

    private Map<String, Object> subscriptionView(Subscription subscription, Restaurant restaurant) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantId", restaurant.getId());
        result.put("restaurantName", restaurant.getRestaurantName());
        result.put("planName", subscription.getPlan().getName());
        result.put("status", subscription.getStatus().name());
        result.put("startDate", subscription.getStartDate());
        result.put("endDate", subscription.getEndDate());
        return result;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
