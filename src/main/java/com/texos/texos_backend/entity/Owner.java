package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "owners")
@Getter
@Setter
@NoArgsConstructor
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @Column(name = "google_sub", unique = true, length = 255)
    private String googleSub;

    @Column(name = "auth_provider", nullable = false, length = 20)
    private String authProvider = "LOCAL";

    @Column(length = 12)
    private String avatarIcon;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String avatarImage;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

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
}
