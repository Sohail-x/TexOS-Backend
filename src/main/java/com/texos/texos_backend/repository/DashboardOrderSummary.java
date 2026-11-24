package com.texos.texos_backend.repository;

import java.math.BigDecimal;

public interface DashboardOrderSummary {
    Long getTotalOrders();

    Long getNewOrders();

    Long getPreparingOrders();

    Long getReadyOrders();

    Long getServedOrders();

    Long getCancelledOrders();

    Long getPendingPayments();

    Long getPaidOrders();

    BigDecimal getTotalRevenue();
}
