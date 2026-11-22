package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.LoyaltyAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoyaltyAuditLogRepository extends JpaRepository<LoyaltyAuditLog, Long> {
    List<LoyaltyAuditLog> findTop100ByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);
}
