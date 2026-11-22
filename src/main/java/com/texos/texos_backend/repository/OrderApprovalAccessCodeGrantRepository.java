package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.OrderApprovalAccessCodeGrant;
import com.texos.texos_backend.entity.OrderApprovalCodeStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderApprovalAccessCodeGrantRepository
        extends JpaRepository<OrderApprovalAccessCodeGrant, Long> {

    Optional<OrderApprovalAccessCodeGrant> findFirstByRestaurant_IdOrderByCreatedAtDesc(
            Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "restaurant")
    @Query("select grant from OrderApprovalAccessCodeGrant grant where grant.codeHash = :codeHash")
    Optional<OrderApprovalAccessCodeGrant> findByCodeHashForUpdate(
            @Param("codeHash") String codeHash);

    Optional<OrderApprovalAccessCodeGrant> findByCodeHashAndSessionIdAndStatus(
            String codeHash, UUID sessionId, OrderApprovalCodeStatus status);

    boolean existsByCodeHash(String codeHash);

    @Modifying
    @Query("update OrderApprovalAccessCodeGrant grant set grant.status = "
            + "com.texos.texos_backend.entity.OrderApprovalCodeStatus.REVOKED "
            + "where grant.restaurant.id = :restaurantId and grant.status = "
            + "com.texos.texos_backend.entity.OrderApprovalCodeStatus.ACTIVE")
    int revokeActiveByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Modifying
    @Query("update OrderApprovalAccessCodeGrant grant set grant.status = "
            + "com.texos.texos_backend.entity.OrderApprovalCodeStatus.REVOKED "
            + "where grant.id = :grantId and grant.status = "
            + "com.texos.texos_backend.entity.OrderApprovalCodeStatus.ACTIVE")
    int revokeActiveById(@Param("grantId") Long grantId);
}
