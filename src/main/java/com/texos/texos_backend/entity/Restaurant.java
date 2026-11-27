package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String restaurantName;

    @Column(length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(name = "opening_hours", length = 100)
    private String openingHours;

    @Column(length = 100)
    private String email;

    @Column(name = "google_maps_url", length = 2048)
    private String googleMapsUrl;

    @Column(name = "payment_qr_image", columnDefinition = "LONGTEXT")
    private String paymentQrImage;

    @Column(name = "upi_id", length = 255)
    private String upiId;
@Column(nullable = false)
    private boolean active = true;

@Column(name = "deleted_at")
private Instant deletedAt;


    @Column(name = "order_approval_enabled", nullable = false)
    private boolean orderApprovalEnabled;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Restaurant-specific tax configuration.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuPriceTaxMode menuPriceTaxMode = MenuPriceTaxMode.EXCLUSIVE;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    // Optional service charge configuration.
    @Column(nullable = false)
    private boolean serviceChargeEnabled = false;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceChargeRate = BigDecimal.ZERO;

}