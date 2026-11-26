package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Subscription;
import com.texos.texos_backend.entity.SubscriptionStatus;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select subscription.endDate
            from Subscription subscription
            where subscription.restaurant.id = :restaurantId
              and subscription.status in :statuses
            order by subscription.endDate desc
            """)
    List<LocalDateTime> findEndDatesByRestaurantIdAndStatusIn(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<SubscriptionStatus> statuses,
            Pageable pageable);

    @Query("""
            select case when count(subscription) > 0 then true else false end
            from Subscription subscription
            where subscription.restaurant.owner.id = :ownerId
              and subscription.restaurant.active = true
              and subscription.status in :statuses
              and subscription.endDate > :now
            """)
    boolean existsValidSubscriptionForOwner(
            @Param("ownerId") Long ownerId,
            @Param("statuses") List<SubscriptionStatus> statuses,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("update Subscription s set s.status = com.texos.texos_backend.entity.SubscriptionStatus.EXPIRED " +
            "where s.status in :statuses and s.endDate <= :now")
    int expireOverdue(@Param("statuses") List<SubscriptionStatus> statuses,
                      @Param("now") LocalDateTime now);
}
