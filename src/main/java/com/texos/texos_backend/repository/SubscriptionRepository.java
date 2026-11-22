package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Subscription;
import com.texos.texos_backend.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findFirstByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Subscription> findAllByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    Optional<Subscription> findFirstByRestaurantIdAndStatusInOrderByEndDateDesc(
            Long restaurantId, List<SubscriptionStatus> statuses);

    @Modifying
    @Query("update Subscription s set s.status = com.texos.texos_backend.entity.SubscriptionStatus.EXPIRED " +
            "where s.status in :statuses and s.endDate <= :now")
    int expireOverdue(@Param("statuses") List<SubscriptionStatus> statuses,
                      @Param("now") LocalDateTime now);
}
