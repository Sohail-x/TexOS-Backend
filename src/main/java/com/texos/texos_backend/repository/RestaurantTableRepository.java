
package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.RestaurantTable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
            select restaurant.id as restaurantId,
                   restaurant.restaurantName as restaurantName,
                   restaurant.address as restaurantAddress,
                   restaurant.phone as restaurantPhone,
                   restaurant.openingHours as openingHours,
                   restaurant.googleMapsUrl as googleMapsUrl,
                   restaurant.active as restaurantActive,
                   restaurantTable.tableNumber as tableNumber
            from RestaurantTable restaurantTable
            join restaurantTable.restaurant restaurant
            where restaurantTable.qrToken = :qrToken
              and restaurantTable.active = true
            """)
    Optional<CustomerMenuContext> findCustomerMenuContextByQrToken(
            @Param("qrToken") String qrToken
    );

    boolean existsByRestaurantIdAndTableNumber(
            Long restaurantId,
            String tableNumber
    );
}
