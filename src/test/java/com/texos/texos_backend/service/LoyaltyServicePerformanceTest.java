package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.dto.LoyaltyAccountOverview;
import com.texos.texos_backend.dto.LoyaltyOverviewReward;
import com.texos.texos_backend.dto.LoyaltyRedemptionSummary;
import com.texos.texos_backend.dto.LoyaltyTransactionOverview;
import com.texos.texos_backend.dto.LoyaltyTransactionSummary;
import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.repository.CustomerRepository;
import com.texos.texos_backend.repository.LoyaltyAccountRepository;
import com.texos.texos_backend.repository.LoyaltyAuditLogRepository;
import com.texos.texos_backend.repository.LoyaltyPointAllocationRepository;
import com.texos.texos_backend.repository.LoyaltyPointLotRepository;
import com.texos.texos_backend.repository.LoyaltyProgramRepository;
import com.texos.texos_backend.repository.LoyaltyRedemptionRepository;
import com.texos.texos_backend.repository.LoyaltyRewardRepository;
import com.texos.texos_backend.repository.LoyaltyTransactionRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LoyaltyServicePerformanceTest {
    private static final long RESTAURANT_ID = 4L;

    @Mock private LoyaltyProgramRepository programs;
    @Mock private LoyaltyAccountRepository accounts;
    @Mock private LoyaltyRewardRepository rewards;
    @Mock private LoyaltyTransactionRepository transactions;
    @Mock private LoyaltyRedemptionRepository redemptions;
    @Mock private LoyaltyPointLotRepository pointLots;
    @Mock private LoyaltyPointAllocationRepository allocations;
    @Mock private LoyaltyAuditLogRepository auditLogs;
    @Mock private RestaurantOrderRepository orderRepository;
    @Mock private BillService billService;
    @Mock private RestaurantRepository restaurants;
    @Mock private OwnerRepository owners;
    @Mock private CustomerRepository customers;
    @Mock private RestaurantTableRepository tables;
    @Mock private MenuItemRepository menuItems;
    @Mock private CustomerSessionService sessions;
    @Mock private HttpServletRequest request;
    @InjectMocks private LoyaltyService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overviewCombinesTotalsAndUsesBoundedSummaryProjections() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@example.com", "credentials", List.of()));
        Owner owner = new Owner();
        owner.setId(9L);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 10, 0);

        when(owners.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(restaurants.findByIdAndOwnerId(RESTAURANT_ID, owner.getId()))
                .thenReturn(Optional.of(restaurant));
        when(accounts.overviewByRestaurant(RESTAURANT_ID))
                .thenReturn(new LoyaltyAccountOverview(3L, 140L));
        when(transactions.overviewByRestaurant(RESTAURANT_ID))
                .thenReturn(new LoyaltyTransactionOverview(500L, 125L));
        when(rewards.countByRestaurant_IdAndDeletedFalse(RESTAURANT_ID)).thenReturn(2L);
        when(redemptions.countByRestaurant_IdAndStatus(RESTAURANT_ID, "REDEEMED")).thenReturn(1L);
        when(redemptions.findRecentSummariesByRestaurant(eq(RESTAURANT_ID), any(Pageable.class)))
                .thenReturn(List.of(new LoyaltyRedemptionSummary(
                        10L, "Customer", "1234567890", "Free item", 50, "TXR-1",
                        "TX - #0000001", "REDEEMED", createdAt, createdAt, 20L)));
        when(transactions.findRecentSummariesByRestaurant(eq(RESTAURANT_ID), any(Pageable.class)))
                .thenReturn(List.of(new LoyaltyTransactionSummary(
                        30L, "EARN", 100L, "Earned", "TX - #0000001", createdAt)));
        when(rewards.findOverviewRewardsByRestaurant(RESTAURANT_ID))
                .thenReturn(List.of(new LoyaltyOverviewReward(
                        20L, "Free item", null, "FREE_ITEM", 50, null, null,
                        null, null, null, null, null, 2, null, 1, null, null, true)));
        when(transactions.findRecentSummariesByRestaurantAndType(
                eq(RESTAURANT_ID), eq("EARN"), any(Pageable.class)))
                .thenReturn(List.of(new LoyaltyTransactionSummary(
                        30L, "EARN", 100L, "Earned", "TX - #0000001", createdAt)));
        when(transactions.findRecentSummariesByRestaurantAndType(
                eq(RESTAURANT_ID), eq("REDEEM"), any(Pageable.class))).thenReturn(List.of());

        var result = service.overview(RESTAURANT_ID);

        assertEquals(3L, result.get("totalLoyaltyMembers"));
        assertEquals(140L, result.get("availablePointsOutstanding"));
        assertEquals(500L, result.get("totalPointsIssued"));
        assertEquals(125L, result.get("totalPointsRedeemed"));
        assertEquals(1, ((List<?>) result.get("recentRedemptions")).size());
        assertEquals(Set.of(
                "totalLoyaltyMembers", "totalPointsIssued", "totalPointsRedeemed",
                "availablePointsOutstanding", "totalRewardsCreated", "totalSuccessfulRedemptions",
                "recentRedemptions", "recentTransactions", "popularRewards",
                "pointsEarnedOverTime", "pointsRedeemedOverTime"), result.keySet());
        Map<?, ?> popularReward = ((List<?>) result.get("popularRewards")).stream()
                .map(Map.class::cast).findFirst().orElseThrow();
        assertEquals(1L, popularReward.get("redemptions"));
        assertTrue(popularReward.containsKey("imageUrl"));
        assertNull(popularReward.get("imageUrl"));
        assertTrue(popularReward.containsKey("available"));
        assertEquals(Set.of(
                "id", "rewardName", "description", "imageUrl", "rewardType", "pointsRequired",
                "menuItemId", "menuItemName", "discountPercentage", "discountAmount",
                "maximumDiscount", "minimumOrderAmount", "totalQuantity", "remainingQuantity",
                "maxRedemptions", "redemptionCount", "startDate", "expiryDate", "active",
                "available", "redemptions"), popularReward.keySet());
        verify(accounts, times(1)).overviewByRestaurant(RESTAURANT_ID);
        verify(transactions, times(1)).overviewByRestaurant(RESTAURANT_ID);
        verify(accounts, never()).countByRestaurant_Id(RESTAURANT_ID);
        verify(accounts, never()).sumAvailablePointsByRestaurant(RESTAURANT_ID);
        verify(transactions, never()).sumPointsByRestaurantAndType(eq(RESTAURANT_ID), any());
        verify(redemptions, times(1)).findRecentSummariesByRestaurant(
                eq(RESTAURANT_ID), argThat(pageable -> pageable.getPageSize() == 100));
        verify(transactions, times(1)).findRecentSummariesByRestaurant(
                eq(RESTAURANT_ID), argThat(pageable -> pageable.getPageSize() == 20));
        verify(transactions, times(1)).findRecentSummariesByRestaurantAndType(
                eq(RESTAURANT_ID), eq("EARN"),
                argThat(pageable -> pageable.getPageSize() == 8));
    }

    @Test
    void customerHistoryPaginationReturnsHasMoreWithoutDroppingRows() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setRestaurantName("Restaurant");
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        Customer customer = new Customer();
        customer.setId(55L);
        customer.setCustomerName("Customer");
        customer.setMobileNumber("1234567890");
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 10, 0);

        when(tables.findByQrTokenAndActiveTrue("qr-token")).thenReturn(Optional.of(table));
        when(sessions.getActiveSession(request, RESTAURANT_ID))
                .thenReturn(new CustomerSession(55L, RESTAURANT_ID));
        when(customers.findByIdAndRestaurant_Id(55L, RESTAURANT_ID))
                .thenReturn(Optional.of(customer));
        when(transactions.findCustomerSummariesBetween(
                eq(RESTAURANT_ID), eq(55L), any(LocalDateTime.class), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(List.of(
                        new LoyaltyTransactionSummary(1L, "EARN", 10L, "First", "O-1", createdAt),
                        new LoyaltyTransactionSummary(2L, "EARN", 20L, "Second", "O-2", createdAt),
                        new LoyaltyTransactionSummary(3L, "EARN", 30L, "Third", "O-3", createdAt)));

        var result = service.customerStatus(
                "qr-token", request, "2026-01-01", "2026-01-02", 0, 2);

        assertEquals(2, ((List<?>) result.get("transactions")).size());
        assertTrue((Boolean) result.get("transactionsHasMore"));
        verify(transactions).findCustomerSummariesBetween(
                eq(RESTAURANT_ID), eq(55L), any(LocalDateTime.class), any(LocalDateTime.class),
                argThat(pageable -> pageable.getPageSize() == 3 && pageable.getPageNumber() == 0));
    }
}
