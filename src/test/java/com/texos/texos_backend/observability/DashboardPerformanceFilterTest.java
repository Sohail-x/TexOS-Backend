package com.texos.texos_backend.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.ServletException;

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

    @Test
    void recordsSanitizedEndpointAndNotificationTiming() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/restaurants/987/payments/notifications/next");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            DashboardRequestMeasurements.Measurement measurement =
                    DashboardRequestMeasurements.current();
            assertNotNull(measurement);
            measurement.finishSecurityAtFilter();
            measurement.startMvcHandler();
            long controllerStartedAtNanos =
                    DashboardRequestMeasurements.startControllerTiming();
            long serviceStartedAtNanos = DashboardRequestMeasurements.startService();
            measurement.recordNotificationType("PAYMENT_SUCCESS");
            measurement.recordQuery();
            measurement.addQueryExecutionNanos(1_000_000);
            measurement.addConnectionAcquisitionNanos(500_000);
            DashboardRequestMeasurements.recordServiceDuration(serviceStartedAtNanos);
            DashboardRequestMeasurements.finishControllerTiming(controllerStartedAtNanos);
            measurement.finishMvcHandler();
            measurement.startSerialization();
            ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
        });

        assertEquals(1, registry.get("texos.api.request")
                .tag("endpoint_type", "payment_notification_next")
                .tag("status", "200")
                .tag("outcome", "completed")
                .timer().count());
        assertEquals(1, registry.get("texos.api.hibernate.query.count")
                .tag("endpoint_type", "payment_notification_next")
                .tag("status", "200")
                .tag("outcome", "completed")
                .summary().count());
        assertEquals(1, registry.get("texos.api.hibernate.query.execution")
                .tag("endpoint_type", "payment_notification_next")
                .tag("status", "200")
                .tag("outcome", "completed")
                .timer().count());
        assertNull(DashboardRequestMeasurements.current());
        registry.close();
    }

    @Test
    void excludesOptionsAndRecordsSecurityShortCircuitsSeparately() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        MockHttpServletRequest optionsRequest =
                new MockHttpServletRequest("OPTIONS", "/api/auth/me");
        MockHttpServletResponse optionsResponse = new MockHttpServletResponse();

        filter.doFilter(optionsRequest, optionsResponse, (servletRequest, servletResponse) -> {
            assertNull(DashboardRequestMeasurements.current());
            ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_NO_CONTENT);
        });
        assertTrue(registry.getMeters().isEmpty());

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_UNAUTHORIZED));

        assertEquals(1, registry.get("texos.api.request")
                .tag("endpoint_type", "auth_me")
                .tag("status", "401")
                .tag("outcome", "short_circuit")
                .timer().count());
        registry.close();
    }

    @Test
    void recognizesAnnouncementAndSubscriptionStatusEndpoints() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        Map<String, String> endpoints = Map.of(
                "/api/announcements/active", "announcement_active",
                "/api/subscriptions/status", "subscription_status");

        for (Map.Entry<String, String> endpoint : endpoints.entrySet()) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", endpoint.getKey());
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                DashboardRequestMeasurements.Measurement measurement =
                        DashboardRequestMeasurements.current();
                assertNotNull(measurement);
                measurement.finishSecurityAtFilter();
                measurement.startMvcHandler();
                long controllerStartedAtNanos =
                        DashboardRequestMeasurements.startControllerTiming();
                DashboardRequestMeasurements.finishControllerTiming(controllerStartedAtNanos);
                measurement.finishMvcHandler();
            });

            assertEquals(1, registry.get("texos.api.request")
                    .tag("endpoint_type", endpoint.getValue())
                    .tag("status", "200")
                    .tag("outcome", "completed")
                    .timer().count());
        }
        registry.close();
    }

    @Test
    void marksErrorsAndDoesNotRecordAsyncAsCompletedRequests() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        MockHttpServletRequest errorRequest =
                new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse errorResponse = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(
                errorRequest,
                errorResponse,
                (servletRequest, servletResponse) -> {
                    ((HttpServletResponse) servletResponse).setStatus(
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    throw new ServletException("test failure");
                }));
        assertEquals(1, registry.get("texos.api.request")
                .tag("endpoint_type", "auth_me")
                .tag("status", "500")
                .tag("outcome", "unhandled_error")
                .timer().count());

        MockHttpServletRequest asyncRequest =
                new MockHttpServletRequest("GET", "/api/auth/me");
        asyncRequest.setAsyncSupported(true);
        MockHttpServletResponse asyncResponse = new MockHttpServletResponse();
        filter.doFilter(asyncRequest, asyncResponse, (servletRequest, servletResponse) ->
                ((MockHttpServletRequest) servletRequest).startAsync());

        assertNull(registry.find("texos.api.request")
                .tag("endpoint_type", "auth_me")
                .tag("status", "200")
                .tag("outcome", "completed")
                .timer());
        assertNull(DashboardRequestMeasurements.current());
        registry.close();
    }

    @Test
    void notificationClassificationUsesOnlyFixedLabels() {
        String route = "/api/restaurants/{restaurantId}/payments/notifications/next";

        DashboardRequestMeasurements.Measurement payment =
                DashboardRequestMeasurements.begin(route);
        payment.recordNotificationType("PAYMENT_SUCCESS");
        assertEquals("payment_notification", payment.getNotificationKind());
        DashboardRequestMeasurements.clear();

        DashboardRequestMeasurements.Measurement waiter =
                DashboardRequestMeasurements.begin(route);
        waiter.recordNotificationType("CALL_WAITER");
        assertEquals("waiter_notification", waiter.getNotificationKind());
        DashboardRequestMeasurements.clear();

        DashboardRequestMeasurements.Measurement empty =
                DashboardRequestMeasurements.begin(route);
        empty.recordNotificationType(null);
        assertEquals("empty", empty.getNotificationKind());
        DashboardRequestMeasurements.clear();
    }
}
