package com.texos.texos_backend.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DashboardPerformanceFilterTest {

    @Test
    void recordsRouteScopedTimingsAndQueryCountsWithoutRestaurantIdentifiers() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/restaurants/987/dashboard/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String route = "/api/restaurants/{restaurantId}/dashboard/summary";

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            DashboardRequestMeasurements.Measurement measurement =
                    DashboardRequestMeasurements.current();
            assertNotNull(measurement);
            measurement.finishSecurityIfNeeded();
            long controllerStartedAtNanos = measurement.startControllerMethod();
            measurement.finishControllerMethod(controllerStartedAtNanos);
            long serviceStartedAtNanos = DashboardRequestMeasurements.startService();
            DashboardRequestMeasurements.recordServiceDuration(serviceStartedAtNanos);
            measurement.recordQuery();
            measurement.addQueryExecutionNanos(1_000_000);
            measurement.addConnectionAcquisitionNanos(500_000);
            measurement.startSerialization();
            ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
        });

        assertEquals(1, registry.get("texos.dashboard.request")
                .tag("route", route).tag("status", "200").timer().count());
        assertEquals(1, registry.get("texos.dashboard.hibernate.query.count")
                .tag("route", route).summary().count());
        assertEquals(1, registry.get("texos.dashboard.hibernate.query.execution")
                .tag("route", route).tag("status", "200").timer().count());
        assertEquals(1, registry.get("texos.dashboard.hibernate.connection.acquisition")
                .tag("route", route).tag("status", "200").timer().count());
        assertTrue(registry.get("texos.dashboard.response.body.write")
                .tag("route", route).tag("status", "200").timer().totalTime(
                        java.util.concurrent.TimeUnit.NANOSECONDS)
                <= registry.get("texos.dashboard.request")
                        .tag("route", route).tag("status", "200").timer().totalTime(
                                java.util.concurrent.TimeUnit.NANOSECONDS));
        assertNull(DashboardRequestMeasurements.current());
        registry.close();
    }
}
