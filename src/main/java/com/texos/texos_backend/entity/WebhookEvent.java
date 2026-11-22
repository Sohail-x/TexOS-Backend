package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_event_id", nullable = false, unique = true, length = 100)
    private String providerEventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 20)
    private String processingStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        receivedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
