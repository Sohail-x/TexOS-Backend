package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_phonepe_configs")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantPhonePeConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Column(name = "client_id", nullable = false, length = 150)
    private String clientId;

    @Column(name = "client_version", nullable = false)
    private Integer clientVersion;

    @Column(name = "client_secret_ciphertext", nullable = false, length = 512)
    private String clientSecretCiphertext;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(name = "webhook_key_id", nullable = false, length = 150)
    private String webhookKeyId;

    @Column(name = "webhook_secret_ciphertext", nullable = false, length = 512)
    private String webhookSecretCiphertext;

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
