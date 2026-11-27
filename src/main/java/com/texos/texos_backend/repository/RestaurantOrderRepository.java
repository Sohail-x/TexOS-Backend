package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.RestaurantOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, Long> {

    boolean existsByTable_Id(Long tableId);

    @Modifying
    @Query("""
            update RestaurantOrder order
            set order.customer = null,
                order.customerName = null,
                order.customerMobile = null,
                order.customerEmail = null
            where order.customer.id = :customerId
            """)
    int anonymizeCustomerDetails(@Param("customerId") Long customerId);

    @Query("""
            select count(order) as totalOrders,
                   count(case when order.orderStatus = :newStatus then order.id end) as newOrders,
                   count(case when order.orderStatus = :preparingStatus then order.id end) as preparingOrders,
                   count(case when order.orderStatus = :readyStatus then order.id end) as readyOrders,
                   count(case when order.orderStatus = :servedStatus then order.id end) as servedOrders,
                   count(case when order.orderStatus = :cancelledStatus then order.id end) as cancelledOrders,
                   count(case when order.paymentStatus = :pendingPaymentStatus then order.id end) as pendingPayments,
                   count(case when order.paymentStatus = :paidPaymentStatus then order.id end) as paidOrders,
                   coalesce(sum(case
                       when order.paymentStatus = :paidPaymentStatus
                        and order.orderStatus <> :cancelledStatus
                       then order.totalAmount
                       else null
                   end), 0) as totalRevenue
            from RestaurantOrder order
            where order.restaurant.id = :restaurantId
              and order.createdAt >= :start and order.createdAt < :end
              and order.approvalStatus not in :excludedStatuses
            """)
    DashboardOrderSummary getDashboardOrderSummary(
            @Param("restaurantId") Long restaurantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("excludedStatuses") List<OrderApprovalStatus> excludedStatuses,
            @Param("newStatus") OrderStatus newStatus,
            @Param("preparingStatus") OrderStatus preparingStatus,
            @Param("readyStatus") OrderStatus readyStatus,
            @Param("servedStatus") OrderStatus servedStatus,
            @Param("cancelledStatus") OrderStatus cancelledStatus,
            @Param("pendingPaymentStatus") PaymentStatus pendingPaymentStatus,
            @Param("paidPaymentStatus") PaymentStatus paidPaymentStatus);

        Optional<RestaurantOrder> findByOrderNumber(String orderNumber);

        List<RestaurantOrder> findAllByOrderNumberInAndRestaurant_IdOrderByCreatedAtDesc(
                Collection<String> orderNumbers,
                Long restaurantId
        );

        List<RestaurantOrder> findAllByCustomer_IdAndRestaurant_IdOrderByCreatedAtDesc(
                Long customerId,
                Long restaurantId
        );

        List<RestaurantOrder> findAllByCustomer_IdAndRestaurant_IdAndTable_IdOrderByCreatedAtDesc(
                Long customerId,
                Long restaurantId,
                Long tableId
        );

        List<RestaurantOrder> findAllByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

        @Query("""
                select distinct orderRow
                from RestaurantOrder orderRow
                join fetch orderRow.restaurant restaurant
                join fetch restaurant.owner
                left join fetch orderRow.customer customer
                where restaurant.id in :restaurantIds
                order by orderRow.createdAt desc
                """)
        List<RestaurantOrder> findComplianceOrdersByRestaurantIds(
                @Param("restaurantIds") Collection<Long> restaurantIds);

        @Query("""
                select distinct orderRow
                from RestaurantOrder orderRow
                join fetch orderRow.restaurant restaurant
                join fetch restaurant.owner
                left join fetch orderRow.customer customer
                where (:name is null or lower(coalesce(orderRow.customerName, customer.customerName, '')) like lower(concat('%', :name, '%')))
                  and (:email is null or lower(coalesce(orderRow.customerEmail, customer.email, '')) like lower(concat('%', :email, '%')))
                  and (:phone is null or coalesce(orderRow.customerMobile, customer.mobileNumber, '') like concat('%', :phone, '%'))
                order by orderRow.createdAt desc
                """)
        List<RestaurantOrder> findComplianceOrdersByCustomerDetails(
                @Param("name") String customerName,
                @Param("email") String customerEmail,
                @Param("phone") String customerPhone);

        @EntityGraph(attributePaths = {"restaurant", "restaurant.owner", "customer"})
        List<RestaurantOrder> findAllByCustomer_IdInOrderByCreatedAtDesc(
                Collection<Long> customerIds);

        Optional<RestaurantOrder> findByIdAndRestaurantId(Long orderId, Long restaurantId);

        List<RestaurantOrder> findAllByRestaurant_IdAndTable_IdOrderByCreatedAtDesc(Long restaurantId, Long tableId);

    boolean existsByRestaurant_IdAndTable_IdAndOrderSessionKeyAndPaymentStatus(
            Long restaurantId,
            Long tableId,
            String orderSessionKey,
            PaymentStatus paymentStatus);

    List<RestaurantOrder> findAllByRestaurant_IdAndTable_IdAndOrderSessionKeyOrderByCreatedAtAsc(
            Long restaurantId,
            Long tableId,
            String orderSessionKey);

    @EntityGraph(attributePaths = {"items", "items.menuItem", "restaurant", "table"})
    List<RestaurantOrder> findAllByRestaurant_IdAndOrderSessionKeyInOrderByCreatedAtAsc(
            Long restaurantId,
            Collection<String> orderSessionKeys);

    List<RestaurantOrder> findAllByRestaurant_IdAndApprovalStatusOrderByCreatedAtAsc(
                Long restaurantId, OrderApprovalStatus approvalStatus);

    @Query("""
            select distinct order
            from RestaurantOrder order
            left join fetch order.items item
            left join fetch order.restaurant
            left join fetch order.table
            where order.restaurant.id = :restaurantId
              and order.approvalStatus = :approvalStatus
            order by order.createdAt asc
            """)
    List<RestaurantOrder> findAllByRestaurant_IdAndApprovalStatusWithDetailsOrderByCreatedAtAsc(
            @Param("restaurantId") Long restaurantId,
            @Param("approvalStatus") OrderApprovalStatus approvalStatus);

        List<RestaurantOrder> findAllByRestaurant_IdAndApprovalStatusNotInOrderByCreatedAtDesc(
                Long restaurantId, Collection<OrderApprovalStatus> excludedStatuses);

        @Query("""
                select distinct order
                from RestaurantOrder order
                left join fetch order.items item
                left join fetch order.restaurant
                left join fetch order.table
                where order.restaurant.id = :restaurantId
                  and order.approvalStatus not in :excludedStatuses
                order by order.createdAt desc
                """)
        List<RestaurantOrder> findAllOwnerOrdersWithDetails(
                @Param("restaurantId") Long restaurantId,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses);

        @Query("""
                select distinct order
                from RestaurantOrder order
                left join fetch order.items item
                left join fetch order.restaurant
                left join fetch order.table
                where order.restaurant.id = :restaurantId
                  and order.orderStatus in :statuses
                  and order.approvalStatus not in :excludedStatuses
                order by order.createdAt desc
                """)
        List<RestaurantOrder> findKitchenOrdersWithDetails(
                @Param("restaurantId") Long restaurantId,
                @Param("statuses") Collection<OrderStatus> statuses,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from RestaurantOrder o where o.id = :orderId and o.restaurant.id = :restaurantId")
        Optional<RestaurantOrder> findByIdAndRestaurantIdForApproval(
                @Param("orderId") Long orderId,
                @Param("restaurantId") Long restaurantId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from RestaurantOrder o where o.id = :orderId and o.restaurant.id = :restaurantId")
        Optional<RestaurantOrder> findByIdAndRestaurantIdForUpdate(
                @Param("orderId") Long orderId,
                @Param("restaurantId") Long restaurantId);

        List<RestaurantOrder>
        findAllByCustomer_IdAndRestaurant_IdAndOrderStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                Long customerId,
                Long restaurantId,
                OrderStatus orderStatus,
                LocalDateTime createdAt);

        List<RestaurantOrder> findTop10ByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);

        @Query("""
                select distinct order
                from RestaurantOrder order
                left join fetch order.items item
                left join fetch order.restaurant
                left join fetch order.table
                where order.id in :orderIds
                """)
        List<RestaurantOrder> findAllWithItemsRestaurantAndTableByIdIn(
                @Param("orderIds") Collection<Long> orderIds);

        @Query("""
                select distinct order
                from RestaurantOrder order
                left join fetch order.items item
                left join fetch order.restaurant
                left join fetch order.table
                where order.restaurant.id = :restaurantId
                  and order.orderStatus in :statuses
                order by order.createdAt desc
                """)
        List<RestaurantOrder> findAllByRestaurant_IdAndOrderStatusInOrderByCreatedAtDesc(
                @Param("restaurantId") Long restaurantId,
                @Param("statuses") List<OrderStatus> statuses);


        long countByRestaurant_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long restaurantId, LocalDateTime start, LocalDateTime end);

        long countByRestaurant_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndOrderStatus(Long restaurantId, LocalDateTime start, LocalDateTime end,
        OrderStatus status);

        long countByRestaurant_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndPaymentStatus(
        Long restaurantId,
        LocalDateTime start,
        LocalDateTime end,
        PaymentStatus status
        );

        @Query("""
                SELECT COALESCE(SUM(o.totalAmount), 0)
                FROM RestaurantOrder o
                WHERE o.restaurant.id = :restaurantId
                AND o.createdAt >= :start
                AND o.createdAt < :end
                AND o.paymentStatus = :paymentStatus
                AND o.orderStatus <> :excludedStatus
        """)
        BigDecimal calculateRevenue(
                @Param("restaurantId") Long restaurantId,
                @Param("start") LocalDateTime start,
                @Param("end") LocalDateTime end,
                @Param("paymentStatus") PaymentStatus paymentStatus,
                @Param("excludedStatus") OrderStatus excludedStatus
        );


        @Query("""
                select order.id
                from RestaurantOrder order
                where order.restaurant.id = :restaurantId
                  and order.approvalStatus not in :excludedStatuses
                order by order.createdAt desc
                """)
        List<Long> findOwnerOrderIdsPage(
                @Param("restaurantId") Long restaurantId,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses,
                Pageable pageable);

        @Query("""
                select order.id
                from RestaurantOrder order
                where order.restaurant.id = :restaurantId
                  and order.approvalStatus not in :excludedStatuses
                  and order.createdAt >= :start
                  and order.createdAt < :end
                order by order.createdAt desc
                """)
        List<Long> findOwnerOrderIdsPageInDateRange(
                @Param("restaurantId") Long restaurantId,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses,
                @Param("start") LocalDateTime start,
                @Param("end") LocalDateTime end,
                Pageable pageable);

        @Query("""
                select order.id
                from RestaurantOrder order
                where order.restaurant.id = :restaurantId
                  and order.approvalStatus not in :excludedStatuses
                  and order.createdAt >= :start
                order by order.createdAt desc
                """)
        List<Long> findOwnerOrderIdsPageFrom(
                @Param("restaurantId") Long restaurantId,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses,
                @Param("start") LocalDateTime start,
                Pageable pageable);

        @Query("""
                select order.id
                from RestaurantOrder order
                where order.restaurant.id = :restaurantId
                  and order.approvalStatus not in :excludedStatuses
                  and order.createdAt < :end
                order by order.createdAt desc
                """)
        List<Long> findOwnerOrderIdsPageBefore(
                @Param("restaurantId") Long restaurantId,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses,
                @Param("end") LocalDateTime end,
                Pageable pageable);

        @Query("""
                select order.orderStatus as status, count(order.id) as orderCount
                from RestaurantOrder order
                where order.restaurant.id = :restaurantId
                  and order.approvalStatus not in :excludedStatuses
                  and (:start is null or order.createdAt >= :start)
                  and (:end is null or order.createdAt < :end)
                group by order.orderStatus
                """)
        List<Object[]> countOrdersByStatusForAnalytics(
                @Param("restaurantId") Long restaurantId,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses,
                @Param("start") LocalDateTime start,
                @Param("end") LocalDateTime end);

        @Query("""
                select count(order.id),
                       count(case when order.paymentStatus = :paidStatus then order.id end),
                       count(case when order.paymentStatus = :pendingStatus then order.id end),
                       count(case when order.orderStatus = :cancelledStatus then order.id end),
                       coalesce(sum(case
                           when order.paymentStatus = :paidStatus
                            and order.orderStatus <> :cancelledStatus
                           then order.totalAmount
                           else null
                       end), 0)
                from RestaurantOrder order
                where order.restaurant.id = :restaurantId
                  and order.approvalStatus not in :excludedStatuses
                  and (:start is null or order.createdAt >= :start)
                  and (:end is null or order.createdAt < :end)
                """)
        List<Object[]> getAnalyticsTotals(
                @Param("restaurantId") Long restaurantId,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses,
                @Param("start") LocalDateTime start,
                @Param("end") LocalDateTime end,
                @Param("paidStatus") PaymentStatus paidStatus,
                @Param("pendingStatus") PaymentStatus pendingStatus,
                @Param("cancelledStatus") OrderStatus cancelledStatus);

        @Query("""
                select order.id
                from RestaurantOrder order
                where order.restaurant.id = :restaurantId
                  and order.approvalStatus not in :excludedStatuses
                  and (:start is null or order.createdAt >= :start)
                  and (:end is null or order.createdAt < :end)
                order by order.createdAt desc
                """)
        List<Long> findAnalyticsOrderIdsPage(
                @Param("restaurantId") Long restaurantId,
                @Param("excludedStatuses") Collection<OrderApprovalStatus> excludedStatuses,
                @Param("start") LocalDateTime start,
                @Param("end") LocalDateTime end,
                Pageable pageable);
}
