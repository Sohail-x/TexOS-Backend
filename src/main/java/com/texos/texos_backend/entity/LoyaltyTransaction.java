package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="loyalty_transactions", uniqueConstraints=@UniqueConstraint(name="uk_loyalty_transaction_idempotency",columnNames="idempotency_key"), indexes=@Index(name="idx_loyalty_tx_customer",columnList="restaurant_id,customer_id,created_at"))
@Getter @Setter @NoArgsConstructor
public class LoyaltyTransaction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="restaurant_id",nullable=false) private Restaurant restaurant;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="customer_id",nullable=false) private Customer customer;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") private RestaurantOrder order;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="redemption_id") private LoyaltyRedemption redemption;
 @Column(name="transaction_type",nullable=false,length=30) private String transactionType;
 @Column(nullable=false) private Long points;
 @Column(length=500) private String description;
 @Column(name="idempotency_key",nullable=false,length=150) private String idempotencyKey;
 @Column(nullable=false,updatable=false) private LocalDateTime createdAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();}
}
