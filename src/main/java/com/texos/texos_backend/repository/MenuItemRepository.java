
package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findAllByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    List<MenuItem> findAllByCategoryIdAndAvailableTrueOrderByDisplayOrderAsc(
            Long categoryId
    );

    Optional<MenuItem> findByIdAndCategoryId(Long id, Long categoryId);

    boolean existsByCategoryId(Long categoryId);

    @Query("""
        SELECT item
        FROM MenuItem item
        WHERE item.category.restaurant.id = :restaurantId
        ORDER BY item.category.displayOrder ASC, item.displayOrder ASC
    """)
    List<MenuItem> findAllByRestaurantId(
            @Param("restaurantId") Long restaurantId
    );
}
