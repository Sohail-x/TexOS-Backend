package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByOrderOrderNumber(String orderNumber);

    List<Bill> findAllByOrder_IdIn(Collection<Long> orderIds);

    Optional<Bill> findByOrderIdAndOrderRestaurantId(
            Long orderId,
            Long restaurantId
    );

    boolean existsByBillNumber(String billNumber);

}
