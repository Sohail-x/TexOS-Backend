package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @Query("""
        SELECT item
        FROM MenuItem item
        JOIN FETCH item.category category
        WHERE category.id = :categoryId
        ORDER BY item.displayOrder ASC
    """)
    List<MenuItem> findAllByCategoryIdOrderByDisplayOrderAsc(
            @Param("categoryId") Long categoryId
    );

    List<MenuItem> findAllByCategoryIdAndAvailableTrueOrderByDisplayOrderAsc(
            Long categoryId
    );

    @Query("""
        SELECT item
        FROM MenuItem item
        JOIN FETCH item.category category
        WHERE category.id IN :categoryIds
          AND item.available = true
        ORDER BY item.displayOrder ASC
    """)
    List<MenuItem> findAllAvailableByCategoryIdInOrderByDisplayOrderAsc(
            @Param("categoryIds") Collection<Long> categoryIds
    );

    @Query("""
        SELECT item
        FROM MenuItem item
        JOIN FETCH item.category category
        JOIN FETCH category.restaurant restaurant
        WHERE item.id IN :itemIds
    """)
    List<MenuItem> findAllWithCategoryAndRestaurantByIdIn(
            @Param("itemIds") Collection<Long> itemIds
    );

    @Query("""
        SELECT item
        FROM MenuItem item
        JOIN FETCH item.category category
        WHERE item.id = :id
          AND category.id = :categoryId
    """)
    Optional<MenuItem> findByIdAndCategoryId(
            @Param("id") Long id,
            @Param("categoryId") Long categoryId
    );

    boolean existsByCategoryId(Long categoryId);

    @Query("""
        SELECT item
        FROM MenuItem item
        JOIN FETCH item.category category
        WHERE category.restaurant.id = :restaurantId
        ORDER BY category.displayOrder ASC, item.displayOrder ASC
    """)
    List<MenuItem> findAllByRestaurantId(
            @Param("restaurantId") Long restaurantId
    );
}
