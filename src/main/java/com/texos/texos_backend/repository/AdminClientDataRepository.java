package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.RestaurantOrder;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminClientDataRepository extends JpaRepository<RestaurantOrder, Long> {
    @Query("""
        select distinct o from RestaurantOrder o
        left join fetch o.items
        left join fetch o.customer
        where o.restaurant.id = :restaurantId
          and o.createdAt >= :start and o.createdAt < :end
          and o.approvalStatus not in (com.texos.texos_backend.entity.OrderApprovalStatus.PENDING,
                                       com.texos.texos_backend.entity.OrderApprovalStatus.REJECTED)
        order by o.createdAt desc
        """)
    List<RestaurantOrder> findClientDataOrders(
        @Param("restaurantId") Long restaurantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
