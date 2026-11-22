package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "restaurant_orders",
    indexes = {
        @Index(name = "idx_order_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_order_created", columnList = "created_at"),
        @Index(name = "idx_order_customer", columnList = "customer_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class RestaurantOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    // Nullable so all existing orders remain valid after this module is added.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;


    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_mobile", length = 20)
    private String customerMobile;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "order_session_key", length = 36)
    private String orderSessionKey;



    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus orderStatus = OrderStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 24)
    private OrderApprovalStatus approvalStatus = OrderApprovalStatus.NOT_REQUIRED;

    @Column(name = "approval_updated_at")
    private LocalDateTime approvalUpdatedAt;

    @Column(name = "approval_session_id")
    private UUID approvalSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "reward_discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rewardDiscountAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (orderNumber == null || orderNumber.isBlank()) {
            orderNumber = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
