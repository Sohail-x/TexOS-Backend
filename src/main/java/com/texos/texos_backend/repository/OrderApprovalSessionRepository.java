package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.OrderApprovalSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderApprovalSessionRepository extends JpaRepository<OrderApprovalSession, UUID> {

    @EntityGraph(attributePaths = "restaurant")
    Optional<OrderApprovalSession> findBySessionId(UUID sessionId);

    @Modifying
    @Query("update OrderApprovalSession s set s.revokedAt = CURRENT_TIMESTAMP "
            + "where s.restaurant.id = :restaurantId and s.revokedAt is null")
    int revokeActiveByRestaurantId(@Param("restaurantId") Long restaurantId);
}
