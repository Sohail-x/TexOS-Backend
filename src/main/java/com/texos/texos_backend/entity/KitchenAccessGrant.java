package com.texos.texos_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "kitchen_access_grants",
        indexes = @Index(name = "idx_kitchen_grant_restaurant_created", columnList = "restaurant_id, created_at"),
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_kitchen_grant_token_hash", columnNames = "token_hash"),
            @UniqueConstraint(name = "uk_kitchen_grant_session_id", columnNames = "session_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class KitchenAccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KitchenAccessStatus status = KitchenAccessStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "session_expires_at")
    private LocalDateTime sessionExpiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
