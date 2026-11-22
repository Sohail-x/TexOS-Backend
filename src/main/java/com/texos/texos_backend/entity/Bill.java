package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bills")
@Getter
@Setter
@NoArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String billNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private RestaurantOrder order;

    @Column(nullable = false)
    private Integer billVersion = 1;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuPriceTaxMode taxMode;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false)
    private boolean serviceChargeEnabled;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceChargeRate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceChargeAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "reward_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rewardDiscountAmount = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (billNumber == null) {
            billNumber = UUID.randomUUID().toString();
        }

        createdAt = LocalDateTime.now();
    }
}