package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Customer;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.Instant;
import java.time.LocalDateTime;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByRestaurant_IdAndMobileNumber(
            Long restaurantId,
            String mobileNumber
    );

    Optional<Customer> findByRestaurant_IdAndMobileNumberAndActiveTrueAndDeletedAtIsNull(
            Long restaurantId,
            String mobileNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Customer> findTop500ByDeletedAtLessThanEqualAndAnonymizedAtIsNullOrderByIdAsc(
            Instant deletedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Customer> findTop500ByActiveTrueAndDeletedAtIsNullAndAnonymizedAtIsNullAndLastActivityAtLessThanEqualOrderByIdAsc(
            LocalDateTime lastActivityAt);

    List<Customer> findAllByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);

    @EntityGraph(attributePaths = "restaurant")
    List<Customer> findAllByRestaurant_IdIn(Collection<Long> restaurantIds);

    @EntityGraph(attributePaths = "restaurant")
    List<Customer> findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();

    @EntityGraph(attributePaths = "restaurant")
    @Query("""
            select customer
            from Customer customer
            where (:name is null or lower(customer.customerName) like lower(concat('%', :name, '%')))
              and (:email is null or lower(coalesce(customer.email, '')) like lower(concat('%', :email, '%')))
              and (:phone is null or coalesce(customer.mobileNumber, '') like concat('%', :phone, '%'))
            order by customer.id
            """)
    List<Customer> findForComplianceSearch(
            @Param("name") String name,
            @Param("email") String email,
            @Param("phone") String phone);

    @Query("""
            select customer
            from Customer customer
            where customer.id in :customerIds
              and customer.restaurant.id = :restaurantId
            order by customer.createdAt desc
            """)
    List<Customer> findAllByIdInAndRestaurant_IdOrderByCreatedAtDesc(
            @Param("customerIds") Collection<Long> customerIds,
            @Param("restaurantId") Long restaurantId
    );

    Optional<Customer> findByIdAndRestaurant_Id(Long customerId, Long restaurantId);

    boolean existsByIdAndRestaurant_IdAndActiveTrueAndDeletedAtIsNull(
            Long customerId, Long restaurantId);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update Customer customer
            set customer.lastActivityAt = :now
            where customer.id = :customerId
              and customer.active = true
              and customer.deletedAt is null
              and (customer.lastActivityAt is null or customer.lastActivityAt < :touchBefore)
            """)
    int touchLastActivity(
            @Param("customerId") Long customerId,
            @Param("touchBefore") LocalDateTime touchBefore,
            @Param("now") LocalDateTime now);

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
