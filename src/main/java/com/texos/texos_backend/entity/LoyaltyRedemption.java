package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="loyalty_redemptions", indexes=@Index(name="idx_loyalty_redemption_restaurant",columnList="restaurant_id,status,created_at"))
@Getter @Setter @NoArgsConstructor
public class LoyaltyRedemption {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="restaurant_id",nullable=false) private Restaurant restaurant;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="customer_id",nullable=false) private Customer customer;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reward_id") private LoyaltyReward reward;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id") private RestaurantOrder order;
 @Column(name="redemption_code",nullable=false,unique=true,length=40) private String redemptionCode;
 @Column(name="points_used",nullable=false) private Integer pointsUsed;
 @Column(name="discount_value",nullable=false,precision=12,scale=2) private BigDecimal discountValue=BigDecimal.ZERO;
 @Column(nullable=false,length=20) private String status="PENDING";
 @Column(name="expires_at") private LocalDateTime expiresAt;
 @Column(nullable=false,updatable=false) private LocalDateTime createdAt;
 @Column(name="redeemed_at") private LocalDateTime redeemedAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();}
}
