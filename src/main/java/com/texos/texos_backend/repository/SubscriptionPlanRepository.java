package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    List<SubscriptionPlan> findAllByActiveTrueOrderByPriceAsc();
    Optional<SubscriptionPlan> findByIdAndActiveTrue(Long id);
    Optional<SubscriptionPlan> findFirstByTrialPlanTrueAndActiveTrue();
}
