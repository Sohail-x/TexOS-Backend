package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Bill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByOrderOrderNumber(String orderNumber);

    Optional<Bill> findByOrderSessionKeyAndSupersededByBillIdIsNull(String orderSessionKey);

    Optional<Bill> findByOrderIdAndOrderRestaurantId(Long orderId, Long restaurantId);

    List<Bill> findAllByOrderSessionKeyInAndSupersededByBillIdIsNull(
            Collection<String> orderSessionKeys);

    @Query("""
            select distinct bill
            from Bill bill
            where bill.supersededByBillId is null
              and (bill.order.id in :orderIds
                   or bill.orderSessionKey in (
                       select restaurantOrder.orderSessionKey
                       from RestaurantOrder restaurantOrder
                       where restaurantOrder.id in :orderIds
                   ))
            """)
    List<Bill> findAllCanonicalBillsForOrderIds(
            @Param("orderIds") Collection<Long> orderIds);

    @EntityGraph(attributePaths = {"order", "order.restaurant"})
    List<Bill> findAllByOrder_IdIn(Collection<Long> orderIds);

    @Query("select bill from Bill bill join fetch bill.order where bill.order.id in :orderIds")
    List<Bill> findAllWithOrderByOrder_IdIn(@Param("orderIds") Collection<Long> orderIds);

    @Query("""
            select distinct bill
            from Bill bill
            join fetch bill.order order
            left join fetch order.items item
            left join fetch item.menuItem
            left join fetch order.restaurant
            left join fetch order.table
            where order.id in :orderIds
            """)
    List<Bill> findAllWithOrderDetailsByOrder_IdIn(
            @Param("orderIds") Collection<Long> orderIds
    );

    boolean existsByBillNumber(String billNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Bill> findTop500ByCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime cutoff);
}
