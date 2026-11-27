package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.dto.PublicMenuItemView;
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
        SELECT new com.texos.texos_backend.dto.PublicMenuItemView(
            category.id,
            item.id,
            item.itemName,
            item.description,
            item.price,
            CASE WHEN item.imageUrl LIKE 'data:image/%' THEN null ELSE item.imageUrl END,
            CASE WHEN item.imageUrl LIKE 'data:image/%' THEN true ELSE false END
        )
        FROM MenuItem item
        JOIN item.category category
        WHERE category.id IN :categoryIds
          AND item.available = true
        ORDER BY item.displayOrder ASC
    """)
    List<PublicMenuItemView> findAllPublicAvailableByCategoryIdInOrderByDisplayOrderAsc(
            @Param("categoryIds") Collection<Long> categoryIds
    );

    @Query("""
        SELECT item.imageUrl
        FROM MenuItem item
        JOIN item.category category
        JOIN category.restaurant restaurant
        WHERE item.id = :itemId
          AND category.active = true
          AND item.available = true
          AND restaurant.active = true
          AND EXISTS (
              SELECT menuTable.id
              FROM RestaurantTable menuTable
              WHERE menuTable.qrToken = :qrToken
                AND menuTable.active = true
                AND menuTable.restaurant.id = restaurant.id
          )
    """)
    Optional<String> findPublicImageDataByIdAndQrToken(
            @Param("itemId") Long itemId,
            @Param("qrToken") String qrToken
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
