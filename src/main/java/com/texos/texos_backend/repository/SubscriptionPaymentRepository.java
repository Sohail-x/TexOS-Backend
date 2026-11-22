package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.SubscriptionPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.LocalDateTime;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    Optional<SubscriptionPayment> findByMerchantOrderId(String merchantOrderId);

    Optional<SubscriptionPayment> findByProviderPaymentId(String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from SubscriptionPayment payment where payment.merchantOrderId = :merchantOrderId")
    Optional<SubscriptionPayment> findByMerchantOrderIdForUpdate(
            @Param("merchantOrderId") String merchantOrderId);

    Optional<SubscriptionPayment> findFirstByRestaurant_IdAndPaymentStatusInOrderByCreatedAtDesc(
            Long restaurantId, Collection<String> statuses);

    List<SubscriptionPayment> findTop100ByPaymentStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<String> statuses, LocalDateTime updatedAt);

    List<SubscriptionPayment> findAllByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
}
