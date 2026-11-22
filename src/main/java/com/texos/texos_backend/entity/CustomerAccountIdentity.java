package com.texos.texos_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "customer_account_identities",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_customer_identity_value", columnNames = {"identity_type", "identity_value"}),
                @UniqueConstraint(name = "uk_customer_identity_account", columnNames = {"identity_type", "customer_id"})
        })
@Getter
@Setter
@NoArgsConstructor
public class CustomerAccountIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identity_type", nullable = false, length = 10)
    private String identityType;

    @Column(name = "identity_value", nullable = false, length = 255)
    private String identityValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
}
