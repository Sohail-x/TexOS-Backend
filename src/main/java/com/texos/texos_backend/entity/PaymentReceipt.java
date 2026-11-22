package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "payment_receipts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_transaction_id", nullable = false, unique = true)
    private CustomerPaymentTransaction paymentTransaction;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 40)
    private String receiptNumber;

    @Column(name = "receipt_json", nullable = false, columnDefinition = "LONGTEXT")
    private String receiptJson;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    void onCreate() {
        generatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
