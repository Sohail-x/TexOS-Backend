package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.RestaurantPaymentNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantPaymentNotificationRepository extends JpaRepository<RestaurantPaymentNotification, Long> {
    Optional<RestaurantPaymentNotification> findFirstByRestaurant_IdAndNotificationStatusOrderByPriorityAscCreatedAtAsc(
            Long restaurantId, String notificationStatus);

    Optional<RestaurantPaymentNotification> findByIdAndRestaurant_Id(Long id, Long restaurantId);

    boolean existsByPaymentTransaction_IdAndNotificationType(Long paymentTransactionId, String notificationType);

    boolean existsByAssistanceRequest_IdAndNotificationType(Long assistanceRequestId, String notificationType);
}
