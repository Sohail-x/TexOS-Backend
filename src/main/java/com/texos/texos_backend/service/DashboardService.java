package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.DashboardSummaryResponse;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;

import java.math.BigDecimal;
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

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Restaurant not found"));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        long totalOrders =
                orderRepository.countVisibleOrdersForDashboard(
                        restaurantId, start, end, HIDDEN_APPROVAL_STATUSES);

        long newOrders = countByStatus(
                restaurantId, start, end, OrderStatus.NEW);

        long preparingOrders = countByStatus(
                restaurantId, start, end, OrderStatus.PREPARING);

        long readyOrders = countByStatus(
                restaurantId, start, end, OrderStatus.READY);

        long servedOrders = countByStatus(
                restaurantId, start, end, OrderStatus.SERVED);

        long cancelledOrders = countByStatus(
                restaurantId, start, end, OrderStatus.CANCELLED);

        long pendingPayments =
                orderRepository.countVisiblePaymentsForDashboard(
                        restaurantId, start, end, PaymentStatus.PENDING, HIDDEN_APPROVAL_STATUSES);

        long paidOrders =
                orderRepository.countVisiblePaymentsForDashboard(
                        restaurantId, start, end, PaymentStatus.PAID, HIDDEN_APPROVAL_STATUSES);

        BigDecimal totalRevenue = orderRepository.calculateVisibleRevenueForDashboard(
                restaurantId,
                start,
                end,
                PaymentStatus.PAID,
                OrderStatus.CANCELLED,
                HIDDEN_APPROVAL_STATUSES
        );

        return new DashboardSummaryResponse(
                restaurant.getId(),
                restaurant.getRestaurantName(),
                today,
                totalOrders,
                newOrders,
                preparingOrders,
                readyOrders,
                servedOrders,
                cancelledOrders,
                pendingPayments,
                paidOrders,
                totalRevenue
        );
    }

    private long countByStatus(
            Long restaurantId,
            LocalDateTime start,
            LocalDateTime end,
            OrderStatus status) {

        return orderRepository.countVisibleOrdersByStatusForDashboard(
                restaurantId, start, end, status, HIDDEN_APPROVAL_STATUSES);
    }
}