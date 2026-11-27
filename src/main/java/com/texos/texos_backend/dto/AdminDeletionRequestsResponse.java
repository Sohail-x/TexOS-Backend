package com.texos.texos_backend.dto;

import java.time.Instant;
import java.util.List;

public record AdminDeletionRequestsResponse(
        List<OwnerDeletionRequest> ownerDeletions,
        List<CustomerDeletionRequest> customerDeletions) {

    public record OwnerDeletionRequest(
            Long ownerId,
            String name,
            String email,
            String phone,
            List<String> restaurantNames,
            Instant deletedAt,
            Instant anonymizedAt,
            long daysRemainingPiiErasure,
            long daysRemainingMetadataPurge) {
    }

    public record CustomerDeletionRequest(
            Long customerId,
            String name,
            String phone,
            String email,
            String restaurantName,
            Instant deletedAt,
            Instant anonymizedAt,
            long daysRemainingPiiErasure) {
    }
}
