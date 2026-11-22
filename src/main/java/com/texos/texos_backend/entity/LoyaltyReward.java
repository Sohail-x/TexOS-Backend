package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="loyalty_rewards", indexes=@Index(name="idx_loyalty_reward_restaurant_active",columnList="restaurant_id,is_active"))
@Getter @Setter @NoArgsConstructor
public class LoyaltyReward {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="restaurant_id",nullable=false) private Restaurant restaurant;
 @Column(name="reward_name",nullable=false,length=120) private String rewardName;
 @Column(length=1000) private String description;
 @Lob @Column(name="image_url",columnDefinition="MEDIUMTEXT") private String imageUrl;
 @Column(name="reward_type",nullable=false,length=30) private String rewardType;
 @Column(name="points_required",nullable=false) private Integer pointsRequired;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="menu_item_id") private MenuItem menuItem;
 @Column(name="discount_percentage",precision=5,scale=2) private BigDecimal discountPercentage;
 @Column(name="discount_amount",precision=12,scale=2) private BigDecimal discountAmount;
 @Column(name="maximum_discount",precision=12,scale=2) private BigDecimal maximumDiscount;
 @Column(name="minimum_order_amount",precision=12,scale=2) private BigDecimal minimumOrderAmount;
 @Column(name="total_quantity") private Integer totalQuantity;
 @Column(name="remaining_quantity") private Integer remainingQuantity;
 @Column(name="max_redemptions") private Integer maxRedemptions;
 @Column(name="redemption_count",nullable=false) private Integer redemptionCount=0;
 @Column(name="start_date") private LocalDateTime startDate;
 @Column(name="expiry_date") private LocalDateTime expiryDate;
 @Column(name="is_active",nullable=false) private boolean active=true;
 @Column(name="is_deleted",nullable=false) private boolean deleted=false;
 @Column(nullable=false,updatable=false) private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();updatedAt=createdAt;}
 @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}
