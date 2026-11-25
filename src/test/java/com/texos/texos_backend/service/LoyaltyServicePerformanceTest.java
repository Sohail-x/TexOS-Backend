package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.LoyaltyRedemption;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @InjectMocks private LoyaltyService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loyaltyOverviewReadsRecentRedemptionsOnceForBothSections() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@example.com", "credentials", List.of()));
        Owner owner = new Owner();
        owner.setId(9L);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        Customer customer = new Customer();
        customer.setCustomerName("Customer");
        customer.setMobileNumber("1234567890");
        LoyaltyRedemption redemption = new LoyaltyRedemption();
        redemption.setId(10L);
        redemption.setCustomer(customer);
        redemption.setPointsUsed(50);
        redemption.setStatus("REDEEMED");

        when(owners.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(restaurants.findByIdAndOwnerId(RESTAURANT_ID, owner.getId()))
                .thenReturn(Optional.of(restaurant));
        when(redemptions.findTop100ByRestaurant_IdOrderByCreatedAtDesc(RESTAURANT_ID))
                .thenReturn(List.of(redemption));
        when(rewards.findAllByRestaurant_IdAndDeletedFalseOrderByCreatedAtDesc(RESTAURANT_ID))
                .thenReturn(List.of());
        when(transactions.findTop20ByRestaurant_IdOrderByCreatedAtDesc(RESTAURANT_ID))
                .thenReturn(List.of());
        when(transactions.findTop100ByRestaurant_IdAndTransactionTypeOrderByCreatedAtDesc(
                RESTAURANT_ID, "EARN")).thenReturn(List.of());
        when(transactions.findTop100ByRestaurant_IdAndTransactionTypeOrderByCreatedAtDesc(
                RESTAURANT_ID, "REDEEM")).thenReturn(List.of());

        var result = service.overview(RESTAURANT_ID);

        assertEquals(1, ((List<?>) result.get("recentRedemptions")).size());
        verify(redemptions, times(1))
                .findTop100ByRestaurant_IdOrderByCreatedAtDesc(RESTAURANT_ID);
    }
}
