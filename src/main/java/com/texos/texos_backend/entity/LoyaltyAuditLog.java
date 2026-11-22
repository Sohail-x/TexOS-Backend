package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_audit_logs", indexes = {
        @Index(name = "idx_loyalty_audit_restaurant_created", columnList = "restaurant_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class LoyaltyAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;
    @Column(name = "actor_identity", nullable = false, length = 255)
    private String actorIdentity;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;
    @Column(name = "target_id")
    private Long targetId;
    @Column(length = 1000)
    private String details;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
