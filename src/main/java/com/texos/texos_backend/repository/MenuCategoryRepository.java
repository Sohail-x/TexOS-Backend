
package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findAllByRestaurantId(Long restaurantId);

    List<MenuCategory> findAllByRestaurantIdOrderByDisplayOrderAsc(Long restaurantId);

    List<MenuCategory> findAllByRestaurantIdAndActiveTrueOrderByDisplayOrderAsc(
            Long restaurantId
    );

    Optional<MenuCategory> findByIdAndRestaurantId(Long id, Long restaurantId);

    Optional<MenuCategory> findByRestaurantIdAndCategoryNameIgnoreCase(
            Long restaurantId, String categoryName);

    boolean existsByRestaurantIdAndCategoryNameIgnoreCase(
            Long restaurantId, String categoryName
    );

    boolean existsByRestaurantIdAndCategoryNameIgnoreCaseAndIdNot(
            Long restaurantId, String categoryName, Long id
    );
}
