package com.texos.texos_backend.repository;
import com.texos.texos_backend.entity.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction,Long>{
 boolean existsByIdempotencyKey(String key);
 Optional<LoyaltyTransaction> findByOrder_IdAndTransactionType(Long orderId,String type);
 List<LoyaltyTransaction> findTop100ByRestaurant_IdAndCustomer_IdOrderByCreatedAtDesc(Long restaurantId,Long customerId);
 List<LoyaltyTransaction> findAllByRestaurant_IdAndCustomer_IdAndCreatedAtBetweenOrderByCreatedAtDesc(Long restaurantId,Long customerId,LocalDateTime from,LocalDateTime to);
 List<LoyaltyTransaction> findTop20ByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);
 List<LoyaltyTransaction> findTop100ByRestaurant_IdAndTransactionTypeOrderByCreatedAtDesc(Long restaurantId,String type);
 long countByRestaurant_IdAndTransactionType(Long restaurantId,String type);
 @Query("select coalesce(sum(t.points),0) from LoyaltyTransaction t where t.restaurant.id=:restaurantId and t.transactionType=:type")
 Long sumPointsByRestaurantAndType(@Param("restaurantId") Long restaurantId,@Param("type") String type);
}
