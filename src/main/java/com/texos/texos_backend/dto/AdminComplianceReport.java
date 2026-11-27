package com.texos.texos_backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record AdminComplianceReport(
        Instant generatedAt,
        Summary summary,
        List<OwnerRecord> owners,
        List<RestaurantRecord> restaurants,
        List<CustomerRecord> customers,
        List<OrderRecord> orders,
        List<BillRecord> bills) {

    public record Summary(
            int ownerCount,
            int restaurantCount,
            int customerCount,
            int orderCount,
            int billCount,
            int loyaltyAccountCount,
            int totalMatchedRecords) {
    }

    public record OwnerRecord(
            Long ownerId,
            String name,
            String email,
            String phone,
            String retentionStatus,
            LocalDateTime createdAt,
            Instant deletedAt,
            Instant anonymizedAt) {
    }

    public record RestaurantRecord(
            Long restaurantId,
            String name,
            String address,
            String phone,
            String email,
            Long ownerId,
            String ownerName,
            String retentionStatus,
            LocalDateTime createdAt,
            Instant deletedAt) {
    }

    public record LoyaltyAccountRecord(
            Long loyaltyAccountId,
            Long availablePoints,
            Long lifetimeEarnedPoints,
            Long lifetimeRedeemedPoints,
            Long pointsDebt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record CustomerRecord(
            Long customerId,
            String name,
            String email,
            String phone,
            Long restaurantId,
            String restaurantName,
            String retentionStatus,
            LocalDateTime createdAt,
            LocalDateTime lastActivityAt,
            Instant deletedAt,
            Instant anonymizedAt,
            List<LoyaltyAccountRecord> loyaltyAccounts) {
    }

    public record OrderRecord(
            Long orderId,
            String orderNumber,
            Long restaurantId,
            String restaurantName,
            Long customerId,
            String customerName,
            String customerEmail,
            String customerPhone,
            String orderStatus,
            String approvalStatus,
            String paymentStatus,
            BigDecimal totalAmount,
            LocalDateTime createdAt) {
    }

    public record BillRecord(
            Long billId,
            String billNumber,
            Long orderId,
            String orderNumber,
            Long restaurantId,
            String restaurantName,
            BigDecimal subtotal,
            String taxMode,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal serviceChargeAmount,
            BigDecimal rewardDiscountAmount,
            BigDecimal totalAmount,
            LocalDateTime createdAt) {
    }
}
