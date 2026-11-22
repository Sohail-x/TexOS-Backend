package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.LoyaltyPointAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyPointAllocationRepository extends JpaRepository<LoyaltyPointAllocation, Long> {
    List<LoyaltyPointAllocation> findAllByRedemption_IdAndStatus(Long redemptionId, String status);
}
