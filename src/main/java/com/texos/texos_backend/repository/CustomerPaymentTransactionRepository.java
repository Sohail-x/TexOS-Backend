package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.CustomerPaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface CustomerPaymentTransactionRepository extends JpaRepository<CustomerPaymentTransaction, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select transaction from CustomerPaymentTransaction transaction
            where transaction.restaurant.id = :restaurantId and transaction.activeKey = :activeKey
            """)
    Optional<CustomerPaymentTransaction> findByRestaurantAndActiveKeyForUpdate(
            @Param("restaurantId") Long restaurantId,
            @Param("activeKey") String activeKey);

    Optional<CustomerPaymentTransaction> findByIdAndRestaurant_Id(Long id, Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from CustomerPaymentTransaction payment where payment.id = :id")
    Optional<CustomerPaymentTransaction> findByIdForUpdate(@Param("id") Long id);

    List<CustomerPaymentTransaction> findAllByRestaurant_IdAndCustomer_IdOrderByCreatedAtDesc(
            Long restaurantId, Long customerId);

    List<CustomerPaymentTransaction> findAllByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);

    Optional<CustomerPaymentTransaction> findFirstByOrders_IdAndRestaurant_IdOrderByCreatedAtDesc(
            Long orderId, Long restaurantId);

    List<CustomerPaymentTransaction> findAllByOrders_IdInAndRestaurant_Id(
            Collection<Long> orderIds, Long restaurantId);
}
