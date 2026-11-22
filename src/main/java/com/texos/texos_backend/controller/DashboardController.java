package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.DashboardSummaryResponse;
import com.texos.texos_backend.dto.OrderResponse;
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

        return dashboardService.getSummary(restaurantId);
    }

    @GetMapping("/recent-orders")
    public List<OrderResponse> getRecentOrders(
            @PathVariable Long restaurantId) {

        return orderService.getRecentOrdersForOwner(restaurantId);
    }


    @GetMapping("/active-orders")
    public List<OrderResponse> getActiveOrders(@PathVariable Long restaurantId) {

        return orderService.getActiveOrdersForOwner(restaurantId);
    }

}