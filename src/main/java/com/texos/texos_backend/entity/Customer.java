package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
    name = "customers",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_customer_restaurant_mobile",
            columnNames = {"restaurant_id", "mobile_number"}
        )
    },
    indexes = {
        @Index(name = "idx_customer_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_customer_mobile", columnList = "mobile_number")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "mobile_number", length = 16)
    private String mobileNumber;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    // Phone is collected as contact information only. It is NOT verified authentication.
    @Column(name = "mobile_verified", nullable = false)
    private boolean mobileVerified = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
        if (lastActivityAt == null) {
            lastActivityAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
