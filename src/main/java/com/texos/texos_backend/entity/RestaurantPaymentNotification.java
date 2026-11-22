package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_payment_notifications")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantPaymentNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "notification_type", nullable = false, length = 30)
    private String notificationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private CustomerPaymentTransaction paymentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistance_request_id")
    private OfflineAssistanceRequest assistanceRequest;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "notification_status", nullable = false, length = 20)
    private String notificationStatus = "PENDING";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
