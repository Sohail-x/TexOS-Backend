package com.texos.texos_backend.repository;
import com.texos.texos_backend.dto.LoyaltyOverviewReward;
import com.texos.texos_backend.entity.LoyaltyReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward,Long>{
 @EntityGraph(attributePaths={"menuItem"})
 List<LoyaltyReward> findAllByRestaurant_IdAndDeletedFalseOrderByCreatedAtDesc(Long restaurantId);
 @EntityGraph(attributePaths={"menuItem"})
 List<LoyaltyReward> findAllByRestaurant_IdAndActiveTrueAndDeletedFalseOrderByCreatedAtDesc(Long restaurantId);
 @Query("select new com.texos.texos_backend.dto.LoyaltyOverviewReward(" +
        "r.id, r.rewardName, r.description, r.rewardType, r.pointsRequired, mi.id, mi.itemName, " +
        "r.discountPercentage, r.discountAmount, r.maximumDiscount, r.minimumOrderAmount, " +
        "r.totalQuantity, r.remainingQuantity, r.maxRedemptions, r.redemptionCount, " +
        "r.startDate, r.expiryDate, r.active) " +
        "from LoyaltyReward r left join r.menuItem mi " +
        "where r.restaurant.id=:restaurantId and r.deleted=false order by r.createdAt desc")
 List<LoyaltyOverviewReward> findOverviewRewardsByRestaurant(
        @Param("restaurantId") Long restaurantId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from LoyaltyReward r where r.id=:id and r.restaurant.id=:restaurantId and r.deleted=false")
 Optional<LoyaltyReward> lockReward(@Param("id") Long id,@Param("restaurantId") Long restaurantId);
 long countByRestaurant_IdAndDeletedFalse(Long restaurantId);
}
