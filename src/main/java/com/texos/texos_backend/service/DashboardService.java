package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.DashboardSummaryResponse;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.DashboardOrderSummary;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final List<OrderApprovalStatus> HIDDEN_APPROVAL_STATUSES =
            List.of(OrderApprovalStatus.PENDING, OrderApprovalStatus.REJECTED);
    private final RestaurantOrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;

    public DashboardService(
            RestaurantOrderRepository orderRepository,
            RestaurantRepository restaurantRepository) {
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long restaurantId) {
        long serviceStartedAtNanos = DashboardRequestMeasurements.startService();
        try {

            Restaurant restaurant = restaurantRepository.findById(restaurantId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Restaurant not found"));

            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.plusDays(1).atStartOfDay();

            DashboardOrderSummary summary = orderRepository.getDashboardOrderSummary(
                    restaurantId,
                    start,
                    end,
                    HIDDEN_APPROVAL_STATUSES,
                    OrderStatus.NEW,
                    OrderStatus.PREPARING,
                    OrderStatus.READY,
                    OrderStatus.SERVED,
                    OrderStatus.CANCELLED,
                    PaymentStatus.PENDING,
                    PaymentStatus.PAID);

            return new DashboardSummaryResponse(
                    restaurant.getId(),
                    restaurant.getRestaurantName(),
                    today,
                    summary.getTotalOrders(),
                    summary.getNewOrders(),
                    summary.getPreparingOrders(),
                    summary.getReadyOrders(),
                    summary.getServedOrders(),
                    summary.getCancelledOrders(),
                    summary.getPendingPayments(),
                    summary.getPaidOrders(),
                    summary.getTotalRevenue()
            );
        } finally {
            DashboardRequestMeasurements.recordServiceDuration(serviceStartedAtNanos);
        }
    }
}