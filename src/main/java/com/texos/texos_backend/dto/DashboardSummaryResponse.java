package com.texos.texos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardSummaryResponse(
        Long restaurantId,
        String restaurantName,
        LocalDate date,
        long totalOrders,
        long newOrders,
        long preparingOrders,
        long readyOrders,
        long servedOrders,
        long cancelledOrders,
        long pendingPayments,
        long paidOrders,
        BigDecimal totalRevenue
) {}