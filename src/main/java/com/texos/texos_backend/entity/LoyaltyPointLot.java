package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_point_lots", indexes = {
        @Index(name = "idx_loyalty_lot_account_expiry", columnList = "account_id,expires_at"),
        @Index(name = "idx_loyalty_lot_order", columnList = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
public class LoyaltyPointLot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private LoyaltyAccount account;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private RestaurantOrder order;
    @Column(name = "earned_points", nullable = false)
    private Long earnedPoints;
    @Column(name = "available_points", nullable = false)
    private Long availablePoints;
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
