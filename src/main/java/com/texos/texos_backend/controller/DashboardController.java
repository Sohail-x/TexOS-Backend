package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.DashboardSummaryResponse;
import com.texos.texos_backend.dto.OrderResponse;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;
import com.texos.texos_backend.service.DashboardService;
import com.texos.texos_backend.service.OrderService;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final OrderService orderService;

    public DashboardController(
            DashboardService dashboardService,
            OrderService orderService) {

        this.dashboardService = dashboardService;
        this.orderService = orderService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary(
            @PathVariable Long restaurantId) {
        long controllerStartedAtNanos = startControllerMethod();
        try {
            return dashboardService.getSummary(restaurantId);
        } finally {
            finishControllerMethod(controllerStartedAtNanos);
        }
    }

    @GetMapping("/recent-orders")
    public List<OrderResponse> getRecentOrders(
            @PathVariable Long restaurantId) {
        long controllerStartedAtNanos = startControllerMethod();
        try {
            return orderService.getRecentOrdersForOwner(restaurantId);
        } finally {
            finishControllerMethod(controllerStartedAtNanos);
        }
    }


    @GetMapping("/active-orders")
    public List<OrderResponse> getActiveOrders(@PathVariable Long restaurantId) {
        long controllerStartedAtNanos = startControllerMethod();
        try {
            return orderService.getActiveOrdersForOwner(restaurantId);
        } finally {
            finishControllerMethod(controllerStartedAtNanos);
        }

    }

    private long startControllerMethod() {
        DashboardRequestMeasurements.Measurement measurement =
                DashboardRequestMeasurements.current();
        if (measurement == null) {
            return 0;
        }
        return measurement.startControllerMethod();
    }

    private void finishControllerMethod(long controllerStartedAtNanos) {
        if (controllerStartedAtNanos == 0) {
            return;
        }
        DashboardRequestMeasurements.Measurement measurement =
                DashboardRequestMeasurements.current();
        if (measurement != null) {
            measurement.finishControllerMethod(controllerStartedAtNanos);
        }
    }

}