package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.RestaurantOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, Long> {

    @Query("""
            select count(order) from RestaurantOrder order
            where order.restaurant.id = :restaurantId
              and order.createdAt >= :start and order.createdAt < :end
              and order.approvalStatus not in :excludedStatuses
            """)
    long countVisibleOrdersForDashboard(
            @Param("restaurantId") Long restaurantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("excludedStatuses") List<OrderApprovalStatus> excludedStatuses);

    @Query("""
            select count(order) from RestaurantOrder order
            where order.restaurant.id = :restaurantId
              and order.createdAt >= :start and order.createdAt < :end
              and order.orderStatus = :orderStatus
              and order.approvalStatus not in :excludedStatuses
            """)
    long countVisibleOrdersByStatusForDashboard(
            @Param("restaurantId") Long restaurantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("excludedStatuses") List<OrderApprovalStatus> excludedStatuses);

    @Query("""
            select count(order) from RestaurantOrder order
            where order.restaurant.id = :restaurantId
              and order.createdAt >= :start and order.createdAt < :end
              and order.paymentStatus = :paymentStatus
              and order.approvalStatus not in :excludedStatuses
            """)
    long countVisiblePaymentsForDashboard(
            @Param("restaurantId") Long restaurantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("excludedStatuses") List<OrderApprovalStatus> excludedStatuses);

    @Query("""
            select coalesce(sum(order.totalAmount), 0) from RestaurantOrder order
            where order.restaurant.id = :restaurantId
              and order.createdAt >= :start and order.createdAt < :end
              and order.paymentStatus = :paidStatus
              and order.orderStatus <> :cancelledStatus
              and order.approvalStatus not in :excludedStatuses
            """)
    BigDecimal calculateVisibleRevenueForDashboard(
            @Param("restaurantId") Long restaurantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("paidStatus") PaymentStatus paidStatus,
            @Param("cancelledStatus") OrderStatus cancelledStatus,
            @Param("excludedStatuses") List<OrderApprovalStatus> excludedStatuses);

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

    List<RestaurantOrder> findAllByRestaurant_IdAndApprovalStatusOrderByCreatedAtAsc(
                Long restaurantId, OrderApprovalStatus approvalStatus);

        List<RestaurantOrder> findAllByRestaurant_IdAndApprovalStatusNotInOrderByCreatedAtDesc(
                Long restaurantId, Collection<OrderApprovalStatus> excludedStatuses);

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

        List<RestaurantOrder> findAllByRestaurant_IdAndOrderStatusInOrderByCreatedAtDesc(Long restaurantId, List<OrderStatus> statuses);


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

}
