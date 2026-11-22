package com.texos.texos_backend.repository;
import com.texos.texos_backend.entity.LoyaltyRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
public interface LoyaltyRedemptionRepository extends JpaRepository<LoyaltyRedemption,Long>{
 List<LoyaltyRedemption> findTop100ByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);
 long countByRestaurant_IdAndStatus(Long restaurantId,String status);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select r from LoyaltyRedemption r where r.status='PENDING' and r.expiresAt is not null and r.expiresAt<=:now")
 List<LoyaltyRedemption> lockExpiredPendingReservations(@Param("now") LocalDateTime now);
 List<LoyaltyRedemption> findAllByOrder_IdAndStatus(Long orderId,String status);
}
