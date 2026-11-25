package com.texos.texos_backend.repository;
import com.texos.texos_backend.dto.LoyaltyAccountOverview;
import com.texos.texos_backend.entity.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount,Long>{
 Optional<LoyaltyAccount> findByRestaurant_IdAndCustomer_Id(Long restaurantId,Long customerId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from LoyaltyAccount a where a.restaurant.id=:restaurantId and a.customer.id=:customerId")
 Optional<LoyaltyAccount> lockAccount(@Param("restaurantId") Long restaurantId,@Param("customerId") Long customerId);
 long countByRestaurant_Id(Long restaurantId);
 @Query("select new com.texos.texos_backend.dto.LoyaltyAccountOverview(count(a), coalesce(sum(a.availablePoints), 0)) from LoyaltyAccount a where a.restaurant.id=:restaurantId")
 LoyaltyAccountOverview overviewByRestaurant(@Param("restaurantId") Long restaurantId);
 @Query("select coalesce(sum(a.availablePoints),0) from LoyaltyAccount a where a.restaurant.id=:restaurantId")
 Long sumAvailablePointsByRestaurant(@Param("restaurantId") Long restaurantId);
}
