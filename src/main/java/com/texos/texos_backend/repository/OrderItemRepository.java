package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.OrderItem;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> findAllByMenuItem_Id(Long menuItemId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItem oi set oi.menuItem = null where oi.menuItem.id = :menuItemId")
    int clearMenuItemReferences(@Param("menuItemId") Long menuItemId);

    @Query("""
            select item.itemName, sum(item.quantity)
            from OrderItem item
            where item.order.restaurant.id = :restaurantId
              and item.order.approvalStatus not in :excludedStatuses
              and (:start is null or item.order.createdAt >= :start)
              and (:end is null or item.order.createdAt < :end)
            group by item.itemName
            order by sum(item.quantity) desc
            """)
    List<Object[]> findTopOrderedItems(
            @Param("restaurantId") Long restaurantId,
            @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);
}
