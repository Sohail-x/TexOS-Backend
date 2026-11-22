package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "offline_assistance_requests")
@Getter
@Setter
@NoArgsConstructor
public class OfflineAssistanceRequest {
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "latest_order_id", nullable = false)
    private RestaurantOrder latestOrder;

    @Column(name = "bill_key", nullable = false, length = 100)
    private String billKey;

    @Column(name = "active_bill_key", length = 100)
    private String activeBillKey;

    @Column(name = "request_status", nullable = false, length = 20)
    private String requestStatus;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_mobile", length = 20)
    private String customerMobile;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "last_recalled_at")
    private LocalDateTime lastRecalledAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private Owner assignedBy;

    @PrePersist
    void onCreate() {
        requestedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
