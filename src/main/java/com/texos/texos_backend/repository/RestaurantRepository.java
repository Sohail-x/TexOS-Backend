package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Restaurant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    @Query("""
            select restaurant.id as id,
                   restaurant.restaurantName as restaurantName
            from Restaurant restaurant
            where restaurant.id = :restaurantId
            """)
    Optional<DashboardRestaurantIdentity> findDashboardIdentityById(
            @Param("restaurantId") Long restaurantId
    );

    List<Restaurant> findAllByOwnerId(Long ownerId);

    @EntityGraph(attributePaths = "owner")
    List<Restaurant> findAllByOwner_IdIn(Collection<Long> ownerIds);

    @EntityGraph(attributePaths = "owner")
    List<Restaurant> findAllByRestaurantNameContainingIgnoreCase(String restaurantName);

    List<Restaurant> findAllByOwnerIdAndActiveTrue(Long ownerId);

    @EntityGraph(attributePaths = "owner")
    List<Restaurant> findAllByActiveTrueOrderByRestaurantNameAsc();

    Optional<Restaurant> findByIdAndOwnerId(Long restaurantId, Long ownerId);

    boolean existsByIdAndOwnerId(Long restaurantId, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select restaurant from Restaurant restaurant where restaurant.id = :restaurantId")
    Optional<Restaurant> findByIdForUpdate(@Param("restaurantId") Long restaurantId);

    @Query(value = """
            select *
            from restaurants
            where id = :restaurantId and owner_id = :ownerId
            for update
            """, nativeQuery = true)
    Optional<Restaurant> findOwnedForUpdate(@Param("restaurantId") Long restaurantId,
                                             @Param("ownerId") Long ownerId);

    @Modifying
    @Query("update Restaurant restaurant set restaurant.active = false where restaurant.id = :restaurantId and restaurant.owner.id = :ownerId")
    int deactivateByIdAndOwnerId(@Param("restaurantId") Long restaurantId, @Param("ownerId") Long ownerId);
}
