package com.texos.texos_backend.dto;

public record AdminComplianceSearchRequest(
        String customerName,
        String customerEmail,
        String customerPhone,
        String ownerName,
        String ownerEmail,
        String ownerPhone,
        String restaurantName) {
}
