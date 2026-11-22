package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByRestaurant_IdAndMobileNumber(
            Long restaurantId,
            String mobileNumber
    );

    List<Customer> findAllByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);

    Optional<Customer> findByIdAndRestaurant_Id(Long customerId, Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :customerId and c.restaurant.id = :restaurantId")
    Optional<Customer> findByIdAndRestaurant_IdForUpdate(
            @Param("customerId") Long customerId,
            @Param("restaurantId") Long restaurantId);

    @Query("""
            SELECT o.customer.id AS customerId,
                   COUNT(o.id) AS totalOrders,
                   COALESCE(SUM(CASE
                       WHEN o.paymentStatus = :paidStatus
                            AND o.orderStatus <> :cancelledStatus
                       THEN o.totalAmount ELSE 0 END), 0) AS totalSpent
            FROM RestaurantOrder o
            WHERE o.restaurant.id = :restaurantId
              AND o.customer.restaurant.id = :restaurantId
              AND o.approvalStatus NOT IN (:pendingApproval, :rejectedApproval)
            GROUP BY o.customer.id
            """)
    List<CustomerOrderMetrics> findOrderMetricsByRestaurantId(
            @Param("restaurantId") Long restaurantId,
            @Param("paidStatus") PaymentStatus paidStatus,
            @Param("cancelledStatus") OrderStatus cancelledStatus,
            @Param("pendingApproval") OrderApprovalStatus pendingApproval,
            @Param("rejectedApproval") OrderApprovalStatus rejectedApproval
    );

    @Query("""
            SELECT o.customer.id AS customerId,
                   COUNT(o.id) AS totalOrders,
                   COALESCE(SUM(CASE
                       WHEN o.paymentStatus = :paidStatus
                            AND o.orderStatus <> :cancelledStatus
                       THEN o.totalAmount ELSE 0 END), 0) AS totalSpent
            FROM RestaurantOrder o
            WHERE o.restaurant.id = :restaurantId
              AND o.customer.restaurant.id = :restaurantId
              AND o.customer.id = :customerId
              AND o.approvalStatus NOT IN (:pendingApproval, :rejectedApproval)
            GROUP BY o.customer.id
            """)
    Optional<CustomerOrderMetrics> findOrderMetricsByRestaurantIdAndCustomerId(
            @Param("restaurantId") Long restaurantId,
            @Param("customerId") Long customerId,
            @Param("paidStatus") PaymentStatus paidStatus,
            @Param("cancelledStatus") OrderStatus cancelledStatus,
            @Param("pendingApproval") OrderApprovalStatus pendingApproval,
            @Param("rejectedApproval") OrderApprovalStatus rejectedApproval
    );
}
