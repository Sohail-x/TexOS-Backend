package com.texos.texos_backend.repository;

import java.math.BigDecimal;

public interface CustomerOrderMetrics {

    Long getCustomerId();

    Long getTotalOrders();

    BigDecimal getTotalSpent();
}
