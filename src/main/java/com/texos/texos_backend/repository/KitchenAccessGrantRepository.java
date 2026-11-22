package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.KitchenAccessGrant;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
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
}
