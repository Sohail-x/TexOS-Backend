package com.texos.texos_backend.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.ServletException;

class DashboardPerformanceFilterTest {

    @Test
    void recordsRouteTimingsPayloadSizeAndQueryCountsWithoutRestaurantIdentifiers() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/restaurants/987/dashboard/recent-orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String route = "/api/restaurants/{restaurantId}/dashboard/recent-orders";

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
            measurement.recordStageDuration(
                    DashboardRequestMeasurements.Measurement.Stage.RECENT_DTO_MAPPING, 1_000);
            measurement.recordOrderPayloadCounts(1, 2, 2);
            measurement.startJsonConversionResponseWindow();
            ((HttpServletResponse) servletResponse).getOutputStream()
                    .write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
            ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
        });

        byte[] responseBody = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        assertEquals("{\"ok\":true}", response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, registry.get("texos.dashboard.request")
                .tag("route", route).tag("status", "200").timer().count());
        assertEquals(1, registry.get("texos.dashboard.hibernate.query.count")
                .tag("route", route).summary().count());
        assertEquals(1, registry.get("texos.dashboard.hibernate.query.execution")
                .tag("route", route).tag("status", "200").timer().count());
        assertEquals(1, registry.get("texos.dashboard.hibernate.connection.acquisition")
                .tag("route", route).tag("status", "200").timer().count());
        assertEquals(1_000,
                registry.get("texos.dashboard.stage.duration")
                        .tag("route", route)
                        .tag("stage", "recent_dto_mapping")
                        .tag("status", "200")
                        .timer()
                        .totalTime(java.util.concurrent.TimeUnit.NANOSECONDS));
        assertEquals(responseBody.length,
                registry.get("texos.dashboard.response.body.bytes")
                        .tag("route", route).tag("status", "200")
                        .summary().totalAmount());
        assertEquals(1, registry.get("texos.dashboard.response.output.write")
                .tag("route", route).tag("status", "200").timer().count());
        assertEquals(1, registry.get("texos.dashboard.json.conversion.response.window")
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
            measurement.startJsonConversionResponseWindow();
            ((HttpServletResponse) servletResponse).getOutputStream()
                    .write("{}".getBytes(StandardCharsets.UTF_8));
            ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
        });

        assertEquals("{}", response.getContentAsString(StandardCharsets.UTF_8));
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
        assertEquals(1, registry.get("texos.api.json.conversion.response.window")
                .tag("endpoint_type", "payment_notification_next")
                .tag("status", "200")
                .tag("outcome", "completed")
                .timer().count());
        assertNull(DashboardRequestMeasurements.current());
        registry.close();
    }

    @Test
    void countsUtf8BytesWrittenThroughTheServletWriter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/restaurants/987/dashboard/active-orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = "{\"name\":\"café\"}".getBytes(StandardCharsets.UTF_8);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse wrappedResponse = (HttpServletResponse) servletResponse;
            wrappedResponse.setContentType("application/json");
            wrappedResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            wrappedResponse.getWriter().write("{\"name\":\"café\"}");
        });

        assertEquals("{\"name\":\"café\"}", response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(body.length,
                registry.get("texos.dashboard.response.body.bytes")
                        .tag("route", "/api/restaurants/{restaurantId}/dashboard/active-orders")
                        .tag("status", "200")
                        .summary().totalAmount());
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
    void instrumentsRewardManagementReadsAndMutationsWithSanitizedEndpointLabels()
            throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        List<RouteCase> routes = List.of(
                new RouteCase("GET", "/api/restaurants/987/loyalty/overview",
                        "owner_loyalty_overview"),
                new RouteCase("GET", "/api/restaurants/987/loyalty/settings",
                        "owner_loyalty_settings"),
                new RouteCase("PUT", "/api/restaurants/987/loyalty/settings",
                        "owner_loyalty_settings_update"),
                new RouteCase("GET", "/api/restaurants/987/loyalty/rewards",
                        "owner_loyalty_rewards"),
                new RouteCase("POST", "/api/restaurants/987/loyalty/rewards",
                        "owner_loyalty_reward_create"),
                new RouteCase("PUT", "/api/restaurants/987/loyalty/rewards/321",
                        "owner_loyalty_reward_update"),
                new RouteCase("PATCH", "/api/restaurants/987/loyalty/rewards/321/status",
                        "owner_loyalty_reward_status"),
                new RouteCase("DELETE", "/api/restaurants/987/loyalty/rewards/321",
                        "owner_loyalty_reward_archive"),
                new RouteCase("GET", "/api/restaurants/987/loyalty/redemptions",
                        "owner_loyalty_redemptions"),
                new RouteCase("GET", "/api/restaurants/987/loyalty/audit-logs",
                        "owner_loyalty_audit_logs"));

        for (RouteCase route : routes) {
            long priorRequestCount = registry.find("texos.api.request")
                    .tag("endpoint_type", route.endpointType())
                    .tag("status", "200")
                    .tag("outcome", "completed")
                    .timer() == null
                    ? 0
                    : registry.get("texos.api.request")
                            .tag("endpoint_type", route.endpointType())
                            .tag("status", "200")
                            .tag("outcome", "completed")
                            .timer().count();
            double priorResponseBytes = registry.find("texos.api.response.body.bytes")
                    .tag("endpoint_type", route.endpointType())
                    .tag("status", "200")
                    .tag("outcome", "completed")
                    .summary() == null
                    ? 0
                    : registry.get("texos.api.response.body.bytes")
                            .tag("endpoint_type", route.endpointType())
                            .tag("status", "200")
                            .tag("outcome", "completed")
                            .summary().totalAmount();
            MockHttpServletRequest request =
                    new MockHttpServletRequest(route.method(), route.path());
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
                DashboardRequestMeasurements.measureRepositoryOperation(
                        DashboardRequestMeasurements.Measurement.Stage.OWNER_REWARDS_LIST,
                        () -> "measured");
                DashboardRequestMeasurements.recordRepositoryResultCount(
                        DashboardRequestMeasurements.Measurement.Stage.OWNER_REWARDS_LIST, 2);
                DashboardRequestMeasurements.recordServiceDuration(serviceStartedAtNanos);
                DashboardRequestMeasurements.finishControllerTiming(controllerStartedAtNanos);
                measurement.finishMvcHandler();
                DashboardRequestMeasurements.recordOwnerResponseCounts(1, 1, 0, 0, 0);
                DashboardRequestMeasurements.recordRewardImagePayload(1, 64, 12);
                measurement.startJsonConversionResponseWindow();
                ((HttpServletResponse) servletResponse).getOutputStream()
                        .write("{}".getBytes(StandardCharsets.UTF_8));
            });

            assertEquals(priorRequestCount + 1, registry.get("texos.api.request")
                    .tag("endpoint_type", route.endpointType())
                    .tag("status", "200")
                    .tag("outcome", "completed")
                    .timer().count());
            assertEquals(priorResponseBytes + 2, registry.get("texos.api.response.body.bytes")
                    .tag("endpoint_type", route.endpointType())
                    .tag("status", "200")
                    .tag("outcome", "completed")
                    .summary().totalAmount());
        }

        assertEquals(1, registry.get("texos.api.response.record.count")
                .tag("value_type", "records")
                .tag("endpoint_type", "owner_loyalty_rewards")
                .tag("status", "200")
                .tag("outcome", "completed")
                .summary().count());
        assertEquals(1, registry.get("texos.api.reward.image.characters")
                .tag("value_type", "data_url")
                .tag("endpoint_type", "owner_loyalty_rewards")
                .tag("status", "200")
                .tag("outcome", "completed")
                .summary().count());
        assertEquals(2, registry.get("texos.api.repository.result.count")
                .tag("value_type", "owner_rewards_repository_call")
                .tag("endpoint_type", "owner_loyalty_rewards")
                .tag("status", "200")
                .tag("outcome", "completed")
                .summary().totalAmount());
        assertTrue(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> "987".equals(tag.getValue())
                        || "321".equals(tag.getValue())));
        assertNull(DashboardRequestMeasurements.current());
        registry.close();
    }

    @Test
    void isolatesConcurrentRewardRequestMeasurements() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        CountDownLatch requestsEntered = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        DashboardSqlInspector sqlInspector = new DashboardSqlInspector();

        try {
            Future<?> overview = executor.submit(() -> executeConcurrentRequest(
                    filter,
                    sqlInspector,
                    "GET",
                    "/api/restaurants/987/loyalty/overview",
                    1,
                    requestsEntered));
            Future<?> redemptions = executor.submit(() -> executeConcurrentRequest(
                    filter,
                    sqlInspector,
                    "GET",
                    "/api/restaurants/654/loyalty/redemptions",
                    4,
                    requestsEntered));
            overview.get(10, TimeUnit.SECONDS);
            redemptions.get(10, TimeUnit.SECONDS);

            assertEquals(1, registry.get("texos.api.hibernate.query.count")
                    .tag("endpoint_type", "owner_loyalty_overview")
                    .tag("status", "200")
                    .tag("outcome", "completed")
                    .summary().totalAmount());
            assertEquals(4, registry.get("texos.api.hibernate.query.count")
                    .tag("endpoint_type", "owner_loyalty_redemptions")
                    .tag("status", "200")
                    .tag("outcome", "completed")
                    .summary().totalAmount());
            assertNull(DashboardRequestMeasurements.current());
        } finally {
            executor.shutdownNow();
            registry.close();
        }
    }

    @Test
    void recordsRewardRequestMeasurementsWhenTheHandlerThrows() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DashboardPerformanceFilter filter =
                new DashboardPerformanceFilter(registry, mock(DataSource.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/restaurants/987/loyalty/rewards");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class, () -> filter.doFilter(
                request, response, (servletRequest, servletResponse) -> {
                    DashboardRequestMeasurements.Measurement measurement =
                            DashboardRequestMeasurements.current();
                    assertNotNull(measurement);
                    measurement.startMvcHandler();
                    DashboardRequestMeasurements.measureRepositoryOperation(
                            DashboardRequestMeasurements.Measurement.Stage.OWNER_REWARDS_LIST,
                            () -> {
                                throw new IllegalStateException("simulated failure");
                            });
                }));

        assertEquals(1, registry.get("texos.api.request")
                .tag("endpoint_type", "owner_loyalty_rewards")
                .tag("status", "200")
                .tag("outcome", "unhandled_error")
                .timer().count());
        assertNull(DashboardRequestMeasurements.current());
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

    private static void executeConcurrentRequest(
            DashboardPerformanceFilter filter,
            DashboardSqlInspector sqlInspector,
            String method,
            String path,
            int queryCount,
            CountDownLatch requestsEntered) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                DashboardRequestMeasurements.Measurement measurement =
                        DashboardRequestMeasurements.current();
                assertNotNull(measurement);
                measurement.finishSecurityAtFilter();
                measurement.startMvcHandler();
                for (int i = 0; i < queryCount; i++) {
                    sqlInspector.inspect("select 1");
                }
                requestsEntered.countDown();
                try {
                    if (!requestsEntered.await(5, TimeUnit.SECONDS)) {
                        throw new ServletException(
                                "Concurrent request did not reach the barrier");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ServletException(exception);
                }
                measurement.finishMvcHandler();
            });
        } catch (IOException | ServletException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record RouteCase(String method, String path, String endpointType) {}
}
