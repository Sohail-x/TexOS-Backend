package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> findAllByMenuItem_Id(Long menuItemId);
}