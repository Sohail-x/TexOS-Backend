package com.texos.texos_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_approval_push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class OrderApprovalPushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private OrderApprovalSession session;

    @Column(name = "endpoint_hash", nullable = false, unique = true, length = 64)
    private String endpointHash;

    @Column(name = "endpoint", nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 256)
    private String p256dh;

    @Column(name = "auth_key", nullable = false, length = 128)
    private String authKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
