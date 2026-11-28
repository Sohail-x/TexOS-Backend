package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.KitchenAccessGrant;
import com.texos.texos_backend.entity.KitchenAccessStatus;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KitchenAccessGrantRepository extends JpaRepository<KitchenAccessGrant, Long> {

    Optional<KitchenAccessGrant> findFirstByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from KitchenAccessGrant grant where grant.tokenHash = :tokenHash")
    Optional<KitchenAccessGrant> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @EntityGraph(attributePaths = "restaurant")
    Optional<KitchenAccessGrant> findBySessionId(UUID sessionId);

    boolean existsByTokenHash(String tokenHash);

    @Query("select count(g) from KitchenAccessGrant g "
            + "where g.restaurant.id = :restaurantId "
            + "and g.status = :status "
            + "and g.revokedAt is null "
            + "and g.sessionExpiresAt > :now")
    long countActiveSessions(
            @Param("restaurantId") Long restaurantId,
            @Param("status") KitchenAccessStatus status,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("update KitchenAccessGrant g set g.status = :revokedStatus, g.revokedAt = :now "
            + "where g.restaurant.id = :restaurantId and g.status = :activeStatus")
    int revokeActiveCodes(
            @Param("restaurantId") Long restaurantId,
            @Param("activeStatus") KitchenAccessStatus activeStatus,
            @Param("revokedStatus") KitchenAccessStatus revokedStatus,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("update KitchenAccessGrant g set g.revokedAt = :now "
            + "where g.restaurant.id = :restaurantId "
            + "and g.status = :usedStatus "
            + "and g.revokedAt is null")
    int revokeActiveSessions(
            @Param("restaurantId") Long restaurantId,
            @Param("usedStatus") KitchenAccessStatus usedStatus,
            @Param("now") LocalDateTime now);
}
