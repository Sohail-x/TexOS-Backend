package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="loyalty_accounts", uniqueConstraints=@UniqueConstraint(name="uk_loyalty_account_restaurant_customer",columnNames={"restaurant_id","customer_id"}))
@Getter @Setter @NoArgsConstructor
public class LoyaltyAccount {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="restaurant_id",nullable=false) private Restaurant restaurant;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="customer_id",nullable=false) private Customer customer;
 @Column(name="available_points",nullable=false) private Long availablePoints=0L;
 @Column(name="lifetime_earned_points",nullable=false) private Long lifetimeEarnedPoints=0L;
 @Column(name="lifetime_redeemed_points",nullable=false) private Long lifetimeRedeemedPoints=0L;
 @Column(name="points_debt",nullable=false) private Long pointsDebt=0L;
 @Version private Long version;
 @Column(nullable=false,updatable=false) private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();updatedAt=createdAt;}
 @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}
