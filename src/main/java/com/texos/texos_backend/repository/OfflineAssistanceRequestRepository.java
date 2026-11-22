package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.OfflineAssistanceRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OfflineAssistanceRequestRepository extends JpaRepository<OfflineAssistanceRequest, Long> {
    Optional<OfflineAssistanceRequest> findByRestaurant_IdAndActiveBillKey(Long restaurantId, String activeBillKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request from OfflineAssistanceRequest request
            where request.id = :requestId and request.restaurant.id = :restaurantId
            """)
    Optional<OfflineAssistanceRequest> findByIdAndRestaurantForUpdate(
            @Param("requestId") Long requestId,
            @Param("restaurantId") Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request from OfflineAssistanceRequest request
            where request.id = :requestId
            """)
    Optional<OfflineAssistanceRequest> findByIdForUpdate(
            @Param("requestId") Long requestId);

    Optional<OfflineAssistanceRequest> findByIdAndOrderSessionKey(Long id, String orderSessionKey);
}
