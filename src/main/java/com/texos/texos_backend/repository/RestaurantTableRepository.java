
package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RestaurantTableRepository
        extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findAllByRestaurantId(Long restaurantId);

    Optional<RestaurantTable> findByIdAndRestaurantId(
            Long tableId,
            Long restaurantId
    );

    Optional<RestaurantTable> findByQrTokenAndActiveTrue(
            String qrToken
    );

    boolean existsByRestaurantIdAndTableNumber(
            Long restaurantId,
            String tableNumber
    );
}
