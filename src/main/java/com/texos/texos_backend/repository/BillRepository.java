package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByOrderOrderNumber(String orderNumber);

    Optional<Bill> findByOrderIdAndOrderRestaurantId(
            Long orderId,
            Long restaurantId
    );

    boolean existsByBillNumber(String billNumber);

}
