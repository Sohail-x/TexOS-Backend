package com.texos.texos_backend.repository;
import com.texos.texos_backend.dto.LoyaltyTransactionOverview;
import com.texos.texos_backend.dto.LoyaltyTransactionSummary;
import com.texos.texos_backend.entity.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
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
 @Query("select new com.texos.texos_backend.dto.LoyaltyTransactionOverview(" +
        "coalesce(sum(case when t.transactionType='EARN' then t.points else 0 end), 0), " +
        "coalesce(sum(case when t.transactionType='REDEEM' then t.points else 0 end), 0)) " +
        "from LoyaltyTransaction t where t.restaurant.id=:restaurantId")
 LoyaltyTransactionOverview overviewByRestaurant(@Param("restaurantId") Long restaurantId);
 @Query("select new com.texos.texos_backend.dto.LoyaltyTransactionSummary(" +
        "t.id, t.transactionType, t.points, t.description, o.orderNumber, t.createdAt) " +
        "from LoyaltyTransaction t left join t.order o " +
        "where t.restaurant.id=:restaurantId order by t.createdAt desc, t.id desc")
 List<LoyaltyTransactionSummary> findRecentSummariesByRestaurant(
        @Param("restaurantId") Long restaurantId, Pageable pageable);
 @Query("select new com.texos.texos_backend.dto.LoyaltyTransactionSummary(" +
        "t.id, t.transactionType, t.points, t.description, o.orderNumber, t.createdAt) " +
        "from LoyaltyTransaction t left join t.order o " +
        "where t.restaurant.id=:restaurantId and t.transactionType=:type " +
        "order by t.createdAt desc, t.id desc")
 List<LoyaltyTransactionSummary> findRecentSummariesByRestaurantAndType(
        @Param("restaurantId") Long restaurantId, @Param("type") String type, Pageable pageable);
 @Query("select new com.texos.texos_backend.dto.LoyaltyTransactionSummary(" +
        "t.id, t.transactionType, t.points, t.description, o.orderNumber, t.createdAt) " +
        "from LoyaltyTransaction t left join t.order o " +
        "where t.restaurant.id=:restaurantId and t.customer.id=:customerId " +
        "order by t.createdAt desc, t.id desc")
 List<LoyaltyTransactionSummary> findCustomerSummaries(
        @Param("restaurantId") Long restaurantId, @Param("customerId") Long customerId,
        Pageable pageable);
 @Query("select new com.texos.texos_backend.dto.LoyaltyTransactionSummary(" +
        "t.id, t.transactionType, t.points, t.description, o.orderNumber, t.createdAt) " +
        "from LoyaltyTransaction t left join t.order o " +
        "where t.restaurant.id=:restaurantId and t.customer.id=:customerId " +
        "and t.createdAt between :from and :to order by t.createdAt desc, t.id desc")
 List<LoyaltyTransactionSummary> findCustomerSummariesBetween(
        @Param("restaurantId") Long restaurantId, @Param("customerId") Long customerId,
        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
 @Query("select new com.texos.texos_backend.dto.LoyaltyTransactionSummary(" +
        "t.id, t.transactionType, t.points, t.description, o.orderNumber, t.createdAt) " +
        "from LoyaltyTransaction t left join t.order o " +
        "where t.restaurant.id=:restaurantId and t.customer.id=:customerId " +
        "and t.createdAt between :from and :to order by t.createdAt desc, t.id desc")
 List<LoyaltyTransactionSummary> findCustomerSummariesBetween(
        @Param("restaurantId") Long restaurantId, @Param("customerId") Long customerId,
        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
 @Query("select coalesce(sum(t.points),0) from LoyaltyTransaction t where t.restaurant.id=:restaurantId and t.transactionType=:type")
 Long sumPointsByRestaurantAndType(@Param("restaurantId") Long restaurantId,@Param("type") String type);
}
