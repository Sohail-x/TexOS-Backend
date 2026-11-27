package com.texos.texos_backend.repository;
import com.texos.texos_backend.dto.LoyaltyRedemptionSummary;
import com.texos.texos_backend.entity.LoyaltyRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
public interface LoyaltyRedemptionRepository extends JpaRepository<LoyaltyRedemption,Long>{
 @Query("select new com.texos.texos_backend.dto.LoyaltyRedemptionSummary(" +
        "r.id, c.customerName, c.mobileNumber, rw.rewardName, r.pointsUsed, " +
        "r.redemptionCode, o.orderNumber, r.status, r.createdAt, r.redeemedAt, rw.id) " +
        "from LoyaltyRedemption r join r.customer c left join r.reward rw left join r.order o " +
        "where r.restaurant.id=:restaurantId order by r.createdAt desc")
 List<LoyaltyRedemptionSummary> findRecentSummariesByRestaurant(
        @Param("restaurantId") Long restaurantId, Pageable pageable);
 long countByRestaurant_IdAndStatus(Long restaurantId,String status);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select r from LoyaltyRedemption r where r.status='PENDING' and r.expiresAt is not null and r.expiresAt<=:now")
 List<LoyaltyRedemption> lockExpiredPendingReservations(@Param("now") LocalDateTime now);
 List<LoyaltyRedemption> findAllByOrder_IdAndStatus(Long orderId,String status);
 List<LoyaltyRedemption> findAllByOrder_IdIn(Collection<Long> orderIds);
}
