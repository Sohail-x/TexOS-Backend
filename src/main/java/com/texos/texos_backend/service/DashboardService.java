package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.DashboardSummaryResponse;
import com.texos.texos_backend.entity.OrderStatus;
import com.texos.texos_backend.entity.OrderApprovalStatus;
import com.texos.texos_backend.entity.PaymentStatus;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.DashboardOrderSummary;
import com.texos.texos_backend.repository.DashboardRestaurantIdentity;
import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.entity.Bill;
import com.texos.texos_backend.entity.RestaurantOrder;
import com.texos.texos_backend.repository.BillRepository;
import com.texos.texos_backend.repository.OrderItemRepository;
import com.texos.texos_backend.repository.RestaurantOrderRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
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
    private final OrderItemRepository orderItemRepository;
    private final BillRepository billRepository;
    private final BillService billService;
    private final OrderService orderService;

    public DashboardService(
            RestaurantOrderRepository orderRepository,
            RestaurantRepository restaurantRepository) {
        this(orderRepository, restaurantRepository, null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DashboardService(
            RestaurantOrderRepository orderRepository,
            RestaurantRepository restaurantRepository,
            OrderItemRepository orderItemRepository,
            BillRepository billRepository,
            BillService billService,
            OrderService orderService) {
        this.orderRepository = orderRepository;
        this.restaurantRepository = restaurantRepository;
        this.orderItemRepository = orderItemRepository;
        this.billRepository = billRepository;
        this.billService = billService;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long restaurantId) {
        long serviceStartedAtNanos = DashboardRequestMeasurements.startService();
        try {

            long identityQueryStartedAtNanos = DashboardRequestMeasurements.startStage();
            DashboardRestaurantIdentity restaurant;
            try {
                restaurant = restaurantRepository
                        .findDashboardIdentityById(restaurantId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException("Restaurant not found"));
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.SUMMARY_IDENTITY_REPOSITORY,
                        identityQueryStartedAtNanos);
            }

            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.plusDays(1).atStartOfDay();

            long aggregateQueryStartedAtNanos = DashboardRequestMeasurements.startStage();
            DashboardOrderSummary summary;
            try {
                summary = orderRepository.getDashboardOrderSummary(
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
            } finally {
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.SUMMARY_AGGREGATE_REPOSITORY,
                        aggregateQueryStartedAtNanos);
            }

            long mappingStartedAtNanos = DashboardRequestMeasurements.startStage();
            try {
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
                DashboardRequestMeasurements.recordStageDuration(
                        DashboardRequestMeasurements.Measurement.Stage.SUMMARY_MAPPING,
                        mappingStartedAtNanos);
            }
        } finally {
            DashboardRequestMeasurements.recordServiceDuration(serviceStartedAtNanos);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAnalytics(Long restaurantId, String period) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (period != null && !"all".equalsIgnoreCase(period)) {
            int days;
            try {
                days = Math.max(1, Integer.parseInt(period));
            } catch (NumberFormatException ex) {
                days = 7;
            }
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            start = today.minusDays(days - 1L).atStartOfDay();
            end = today.plusDays(1).atStartOfDay();
        }

        List<Object[]> totalsRows = orderRepository.getAnalyticsTotals(
                restaurantId,
                HIDDEN_APPROVAL_STATUSES,
                start,
                end,
                PaymentStatus.PAID,
                PaymentStatus.PENDING,
                OrderStatus.CANCELLED);
        Object[] totals = totalsRows.isEmpty() ? new Object[]{0L, 0L, 0L, 0L, BigDecimal.ZERO} : totalsRows.get(0);
        long totalOrders = ((Number) totals[0]).longValue();
        long paidOrders = ((Number) totals[1]).longValue();
        long pendingPayments = ((Number) totals[2]).longValue();
        long cancelledOrders = ((Number) totals[3]).longValue();
        BigDecimal revenue = totals[4] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
        BigDecimal average = paidOrders > 0
                ? revenue.divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<OrderStatus, Long> statusMap = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            statusMap.put(s, 0L);
        }
        for (Object[] row : orderRepository.countOrdersByStatusForAnalytics(
                restaurantId, HIDDEN_APPROVAL_STATUSES, start, end)) {
            if (row[0] instanceof OrderStatus st) {
                statusMap.put(st, ((Number) row[1]).longValue());
            }
        }
        List<Map<String, Object>> statusCounts = new ArrayList<>();
        for (OrderStatus st : List.of(
                OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING,
                OrderStatus.READY, OrderStatus.SERVED, OrderStatus.CANCELLED)) {
            statusCounts.add(Map.of("status", st.name(), "count", statusMap.getOrDefault(st, 0L)));
        }

        List<Map<String, Object>> topItems = new ArrayList<>();
        for (Object[] row : orderItemRepository.findTopOrderedItems(
                restaurantId, HIDDEN_APPROVAL_STATUSES, start, end, PageRequest.of(0, 5))) {
            topItems.add(Map.of(
                    "name", row[0] == null ? "Unnamed item" : String.valueOf(row[0]),
                    "quantity", row[1] == null ? 0L : ((Number) row[1]).longValue()));
        }

        List<Long> orderIds = orderRepository.findAnalyticsOrderIdsPage(
                restaurantId, HIDDEN_APPROVAL_STATUSES, start, end, PageRequest.of(0, 100));
        List<OrderResponse> recentOrders = List.of();
        if (!orderIds.isEmpty()) {
            Map<Long, RestaurantOrder> byId = new LinkedHashMap<>();
            for (RestaurantOrder o : orderRepository.findAllWithItemsRestaurantAndTableByIdIn(orderIds)) {
                byId.put(o.getId(), o);
            }
            recentOrders = orderIds.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .map(o -> {
                        OrderResponse r = new OrderResponse();
                        r.setId(o.getId());
                        r.setOrderNumber(o.getOrderNumber());
                        r.setTableNumber(o.getTable() != null ? o.getTable().getTableNumber() : null);
                        r.setOrderStatus(o.getOrderStatus());
                        r.setPaymentStatus(o.getPaymentStatus());
                        r.setTotalAmount(o.getTotalAmount());
                        r.setCreatedAt(o.getCreatedAt());
                        r.setItems(o.getItems().stream().map(item -> {
                            com.texos.texos_backend.dto.OrderItemResponse ir = new com.texos.texos_backend.dto.OrderItemResponse();
                            ir.setItemName(item.getItemName());
                            ir.setCustomization(item.getCustomization());
                            ir.setQuantity(item.getQuantity());
                            return ir;
                        }).toList());
                        return r;
                    })
                    .toList();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revenue", revenue);
        result.put("total", totalOrders);
        result.put("paid", paidOrders);
        result.put("pending", pendingPayments);
        result.put("cancelled", cancelledOrders);
        result.put("average", average);
        result.put("statusCounts", statusCounts);
        result.put("topItems", topItems);
        result.put("orders", recentOrders);
        return result;
    }
}
