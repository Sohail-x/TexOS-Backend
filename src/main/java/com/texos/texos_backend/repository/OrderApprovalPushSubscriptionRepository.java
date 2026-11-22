package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.OrderApprovalPushSubscription;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderApprovalPushSubscriptionRepository
        extends JpaRepository<OrderApprovalPushSubscription, Long> {

    Optional<OrderApprovalPushSubscription> findByEndpointHash(String endpointHash);

    @EntityGraph(attributePaths = {"session", "session.restaurant"})
    @Query("""
            select subscription from OrderApprovalPushSubscription subscription
            join subscription.session session
            join session.restaurant restaurant
            where restaurant.id = :restaurantId
              and restaurant.orderApprovalEnabled = true
              and restaurant.active = true
              and session.revokedAt is null
              and session.expiresAt > :now
              and exists (
                  select accessCode from OrderApprovalAccessCodeGrant accessCode
                  where accessCode.codeHash = session.accessCodeHash
                    and accessCode.sessionId = session.sessionId
                    and accessCode.status =
                        com.texos.texos_backend.entity.OrderApprovalCodeStatus.USED
              )
            """)
    List<OrderApprovalPushSubscription> findActiveForRestaurant(
            @Param("restaurantId") Long restaurantId,
            @Param("now") LocalDateTime now);

    void deleteBySession_SessionId(UUID sessionId);

    void deleteBySession_Restaurant_Id(Long restaurantId);
}
