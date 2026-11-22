package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_transaction_id", nullable = false)
    private CustomerPaymentTransaction paymentTransaction;

    @Column(name = "merchant_order_id", nullable = false, unique = true, length = 63)
    private String merchantOrderId;

    @Column(name = "provider_order_id", unique = true, length = 100)
    private String providerOrderId;

    @Column(name = "provider_payment_reference", length = 150)
    private String providerPaymentReference;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    @Column(name = "provider_state", length = 30)
    private String providerState;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
