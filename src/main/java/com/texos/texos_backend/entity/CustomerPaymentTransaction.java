package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "restaurant_payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class CustomerPaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "order_session_key", length = 36)
    private String orderSessionKey;

    @Column(name = "bill_key", nullable = false, length = 100)
    private String billKey;

    @Column(name = "active_key", length = 100)
    private String activeKey;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 150)
    private String paymentReference;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "payment_transaction_orders",
            joinColumns = @JoinColumn(name = "payment_transaction_id"),
            inverseJoinColumns = @JoinColumn(name = "order_id"))
    private Set<RestaurantOrder> orders = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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
