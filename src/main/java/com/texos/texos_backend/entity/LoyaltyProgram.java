package com.texos.texos_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="loyalty_programs", uniqueConstraints=@UniqueConstraint(name="uk_loyalty_program_restaurant", columnNames="restaurant_id"))
@Getter @Setter @NoArgsConstructor
public class LoyaltyProgram {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="restaurant_id", nullable=false) private Restaurant restaurant;
 @Column(nullable=false) private boolean enabled=false;
 @Column(name="points_per_100", nullable=false) private Integer pointsPer100=10;
 @Column(name="minimum_order_amount", nullable=false, precision=12, scale=2) private BigDecimal minimumOrderAmount=new BigDecimal("100.00");
 @Column(name="minimum_redemption_points", nullable=false) private Integer minimumRedemptionPoints=100;
 @Column(name="points_expiry_days") private Integer pointsExpiryDays;
 @Column(name="conversion_points", nullable=false) private Integer conversionPoints=100;
 @Column(name="conversion_inr", nullable=false, precision=12, scale=2) private BigDecimal conversionInr=new BigDecimal("10.00");
 @Column(nullable=false, updatable=false) private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 @PrePersist void create(){createdAt=LocalDateTime.now();updatedAt=createdAt;}
 @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}
