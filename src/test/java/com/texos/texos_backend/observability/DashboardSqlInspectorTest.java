package com.texos.texos_backend.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DashboardSqlInspectorTest {

    private final DashboardSqlInspector inspector = new DashboardSqlInspector();

    @AfterEach
    void clearRequestMeasurements() {
        DashboardRequestMeasurements.clear();
    }

    @Test
    void countsHibernateStatementsOnlyWithinDashboardRequestContext() {
        String sql = "select id from restaurant_orders where id = ?";
        DashboardRequestMeasurements.Measurement measurement =
                DashboardRequestMeasurements.begin(
                        "/api/restaurants/{restaurantId}/dashboard/recent-orders");

        assertEquals(sql, inspector.inspect(sql));
        assertEquals(1, measurement.getQueryCount());

        DashboardRequestMeasurements.clear();
        assertEquals(sql, inspector.inspect(sql));
        assertEquals(1, measurement.getQueryCount());
    }

    @Test
    void countsStatementsForNewlyInstrumentedEndpointContext() {
        String sql = "select id from owner where id = ?";
        DashboardRequestMeasurements.Measurement measurement =
                DashboardRequestMeasurements.begin("/api/auth/me");

        assertEquals(sql, inspector.inspect(sql));
        assertEquals(1, measurement.getQueryCount());
    }
}
