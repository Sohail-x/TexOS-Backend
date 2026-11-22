package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.RestaurantPhonePeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantPhonePeConfigRepository extends JpaRepository<RestaurantPhonePeConfig, Long> {
    Optional<RestaurantPhonePeConfig> findByRestaurant_Id(Long restaurantId);
}
