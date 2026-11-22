package com.texos.texos_backend.repository;
import com.texos.texos_backend.entity.LoyaltyReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward,Long>{
 List<LoyaltyReward> findAllByRestaurant_IdAndDeletedFalseOrderByCreatedAtDesc(Long restaurantId);
 List<LoyaltyReward> findAllByRestaurant_IdAndActiveTrueAndDeletedFalseOrderByCreatedAtDesc(Long restaurantId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from LoyaltyReward r where r.id=:id and r.restaurant.id=:restaurantId and r.deleted=false")
 Optional<LoyaltyReward> lockReward(@Param("id") Long id,@Param("restaurantId") Long restaurantId);
 long countByRestaurant_IdAndDeletedFalse(Long restaurantId);
}
