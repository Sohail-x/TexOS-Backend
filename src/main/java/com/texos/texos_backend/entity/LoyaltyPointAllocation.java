package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_point_allocations", indexes = {
        @Index(name = "idx_loyalty_allocation_redemption", columnList = "redemption_id,status")
})
@Getter
@Setter
@NoArgsConstructor
public class LoyaltyPointAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "redemption_id", nullable = false)
    private LoyaltyRedemption redemption;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_lot_id", nullable = false)
    private LoyaltyPointLot pointLot;
    @Column(nullable = false)
    private Long points;
    @Column(nullable = false, length = 20)
    private String status = "RESERVED";
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
