package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "phonepe_webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class PhonePeWebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_hash", nullable = false, unique = true, length = 64)
    private String eventHash;

    @Column(name = "merchant_order_id", nullable = false, length = 63)
    private String merchantOrderId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus = "RECEIVED";

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        receivedAt = LocalDateTime.now();
    }
}
