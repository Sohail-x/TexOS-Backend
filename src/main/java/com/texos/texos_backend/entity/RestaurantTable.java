
package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "restaurant_tables",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_restaurant_table_number",
            columnNames = {"restaurant_id", "table_number"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_number", nullable = false, length = 30)
    private String tableNumber;

    @Column(
        name = "qr_token",
        nullable = false,
        unique = true,
        length = 36
    )
    private String qrToken;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "qr_image", columnDefinition = "LONGBLOB")
    private byte[] qrImage;

    @PrePersist
    protected void onCreate() {
        if (qrToken == null || qrToken.isBlank()) {
            qrToken = UUID.randomUUID().toString();
        }

        createdAt = LocalDateTime.now();
    }
}
