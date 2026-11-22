package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "trial_claims")
@Getter
@Setter
@NoArgsConstructor
public class TrialClaim {
    @Id
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "subscription_id", nullable = false, unique = true)
    private Long subscriptionId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime claimedAt;

    @PrePersist
    void onCreate() {
        claimedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
