package com.texos.texos_backend.observability;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class DashboardPerformanceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DashboardPerformanceFilter.class);
    private static final String REQUEST_ATTRIBUTE =
            DashboardPerformanceFilter.class.getName() + ".measurement";
    private static final String NOTIFICATION_NEXT_ROUTE =
            "/api/restaurants/{restaurantId}/payments/notifications/next";

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;

    public DashboardPerformanceFilter(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        registerPoolGauges();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || routeFor(request) == null;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String route = routeFor(request);
        DashboardRequestMeasurements.Measurement measurement =
                DashboardRequestMeasurements.begin(route);
        request.setAttribute(REQUEST_ATTRIBUTE, measurement);
        boolean chainCompletedNormally = false;

        try {
            filterChain.doFilter(request, response);
            chainCompletedNormally = true;
        } finally {
            try {
                if (request.isAsyncStarted()) {
                    logAsyncRequest(route, response.getStatus());
                } else {
                    if (isDashboardRoute(route)) {
                        measurement.finishSecurityIfNeeded();
                    }
                    String outcome = requestOutcome(
                            chainCompletedNormally, measurement, response.getStatus());
                    if (isDashboardRoute(route)) {
                        recordDashboardRequest(route, response, measurement);
                    } else {
                        recordApiRequest(route, response, measurement, outcome);
                    }
                }
            } finally {
                DashboardRequestMeasurements.clear();
            }
        }
    }

    public static DashboardRequestMeasurements.Measurement currentMeasurement(
            HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof DashboardRequestMeasurements.Measurement measurement
                ? measurement
                : null;
    }

    private void recordDashboardRequest(
            String route,
            HttpServletResponse response,
            DashboardRequestMeasurements.Measurement measurement) {
        long securityNanos = measurement.getSecurityNanos();
        long serviceNanos = measurement.getServiceNanos();
        long controllerNanos = Math.max(
                0, measurement.getControllerMethodNanos() - serviceNanos);
        long mvcHandlerNanos = measurement.getMvcHandlerNanos();
        long queryExecutionNanos = measurement.getQueryExecutionNanos();
        long connectionAcquisitionNanos = measurement.getConnectionAcquisitionNanos();
        long serializationNanos = measurement.getSerializationNanos();
        long queryCount = measurement.getQueryCount();
        int responseOrderCount = measurement.getResponseOrderCount();
        long totalNanos = measurement.getElapsedNanos();
        int status = response.getStatus();

        recordDashboardTimer("texos.dashboard.request", route, status, totalNanos);
        recordDashboardTimer("texos.dashboard.security", route, status, securityNanos);
        recordDashboardTimer("texos.dashboard.controller", route, status, controllerNanos);
        recordDashboardTimer("texos.dashboard.mvc.handler", route, status, mvcHandlerNanos);
        recordDashboardTimer("texos.dashboard.service", route, status, serviceNanos);
        recordDashboardTimer(
                "texos.dashboard.hibernate.query.execution", route, status, queryExecutionNanos);
        recordDashboardTimer(
                "texos.dashboard.hibernate.connection.acquisition",
                route, status, connectionAcquisitionNanos);
        recordDashboardTimer(
                "texos.dashboard.response.body.write", route, status, serializationNanos);
        DistributionSummary.builder("texos.dashboard.hibernate.query.count")
                .tag("route", route)
                .register(meterRegistry)
                .record(queryCount);

        HikariPoolMXBean pool = hikariPool();
        log.atInfo()
                .addKeyValue("event", "dashboard_request")
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("request_total_ms", millis(totalNanos))
                .addKeyValue("security_ms", millis(securityNanos))
                .addKeyValue("controller_ms", millis(controllerNanos))
                .addKeyValue("mvc_handler_ms", millis(mvcHandlerNanos))
                .addKeyValue("service_ms", millis(serviceNanos))
                .addKeyValue("hibernate_query_ms", millis(queryExecutionNanos))
                .addKeyValue("hibernate_query_count", queryCount)
                .addKeyValue("response_order_count", responseOrderCount)
                .addKeyValue("connection_acquisition_ms", millis(connectionAcquisitionNanos))
                .addKeyValue("response_body_write_ms", millis(serializationNanos))
                .addKeyValue("hikari_active", pool == null ? -1 : pool.getActiveConnections())
                .addKeyValue("hikari_idle", pool == null ? -1 : pool.getIdleConnections())
                .addKeyValue("hikari_pending",
                        pool == null ? -1 : pool.getThreadsAwaitingConnection())
                .addKeyValue("application_uptime_ms",
                        ManagementFactory.getRuntimeMXBean().getUptime())
                .log("dashboard_request");
    }

    private void recordApiRequest(
            String route,
            HttpServletResponse response,
            DashboardRequestMeasurements.Measurement measurement,
            String outcome) {
        String endpointType = endpointType(route);
        int status = response.getStatus();
        long securityNanos = measurement.wasSecurityFilterReached()
                ? measurement.getSecurityNanos()
                : 0;
        long serviceNanos = measurement.getServiceNanos();
        long controllerNanos = Math.max(
                0, measurement.getControllerMethodNanos() - serviceNanos);
        long mvcHandlerNanos = measurement.getMvcHandlerNanos();
        long queryExecutionNanos = measurement.getQueryExecutionNanos();
        long connectionAcquisitionNanos = measurement.getConnectionAcquisitionNanos();
        long serializationNanos = measurement.getSerializationNanos();
        long queryCount = measurement.getQueryCount();
        long totalNanos = measurement.getElapsedNanos();

        recordApiTimer("texos.api.request", endpointType, status, outcome, totalNanos);
        recordApiTimer("texos.api.security", endpointType, status, outcome, securityNanos);
        recordApiTimer("texos.api.controller", endpointType, status, outcome, controllerNanos);
        recordApiTimer("texos.api.mvc.handler", endpointType, status, outcome, mvcHandlerNanos);
        recordApiTimer("texos.api.service", endpointType, status, outcome, serviceNanos);
        recordApiTimer("texos.api.hibernate.query.execution",
                endpointType, status, outcome, queryExecutionNanos);
        recordApiTimer("texos.api.hibernate.connection.acquisition",
                endpointType, status, outcome, connectionAcquisitionNanos);
        recordApiTimer("texos.api.response.body.write",
                endpointType, status, outcome, serializationNanos);
        DistributionSummary.builder("texos.api.hibernate.query.count")
                .tag("endpoint_type", endpointType)
                .tag("status", Integer.toString(status))
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(queryCount);

        HikariPoolMXBean pool = hikariPool();
        log.atInfo()
                .addKeyValue("event", "api_request_performance")
                .addKeyValue("endpoint_type", endpointType)
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("outcome", outcome)
                .addKeyValue("timing_relationship", "nested_non_additive")
                .addKeyValue("request_total_ms", millis(totalNanos))
                .addKeyValue("security_ms", millis(securityNanos))
                .addKeyValue("security_timing_available",
                        measurement.wasSecurityFilterReached())
                .addKeyValue("controller_ms", millis(controllerNanos))
                .addKeyValue("mvc_handler_ms", millis(mvcHandlerNanos))
                .addKeyValue("service_ms", millis(serviceNanos))
                .addKeyValue("hibernate_query_ms", millis(queryExecutionNanos))
                .addKeyValue("hibernate_query_count", queryCount)
                .addKeyValue("connection_acquisition_ms", millis(connectionAcquisitionNanos))
                .addKeyValue("response_body_write_ms", millis(serializationNanos))
                .addKeyValue("notification_kind", measurement.getNotificationKind())
                .addKeyValue("hikari_active", pool == null ? -1 : pool.getActiveConnections())
                .addKeyValue("hikari_idle", pool == null ? -1 : pool.getIdleConnections())
                .addKeyValue("hikari_pending",
                        pool == null ? -1 : pool.getThreadsAwaitingConnection())
                .addKeyValue("application_uptime_ms",
                        ManagementFactory.getRuntimeMXBean().getUptime())
                .log("api_request_performance");
    }

    private void logAsyncRequest(String route, int status) {
        log.atInfo()
                .addKeyValue("event", "api_request_performance")
                .addKeyValue("endpoint_type", endpointType(route))
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("outcome", "async_not_instrumented")
                .addKeyValue("timing_relationship", "not_recorded")
                .log("api_request_performance");
    }

    private void recordDashboardTimer(String name, String route, int status, long nanos) {
        Timer.Builder timer = Timer.builder(name)
                .tag("route", route)
                .tag("status", Integer.toString(status));
        if ("texos.dashboard.request".equals(name)) {
            timer.publishPercentiles(0.5, 0.95, 0.99);
        }
        timer.register(meterRegistry).record(nanos, TimeUnit.NANOSECONDS);
    }

    private void recordApiTimer(
            String name, String endpointType, int status, String outcome, long nanos) {
        Timer.Builder timer = Timer.builder(name)
                .tag("endpoint_type", endpointType)
                .tag("status", Integer.toString(status))
                .tag("outcome", outcome);
        if ("texos.api.request".equals(name)) {
            timer.publishPercentiles(0.5, 0.95, 0.99);
        }
        timer.register(meterRegistry).record(nanos, TimeUnit.NANOSECONDS);
    }

    private void registerPoolGauges() {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            return;
        }
        meterRegistry.gauge("texos.datasource.pool.active", hikariDataSource,
                DashboardPerformanceFilter::activeConnections);
        meterRegistry.gauge("texos.datasource.pool.idle", hikariDataSource,
                DashboardPerformanceFilter::idleConnections);
        meterRegistry.gauge("texos.datasource.pool.pending", hikariDataSource,
                DashboardPerformanceFilter::pendingConnections);
    }

    private HikariPoolMXBean hikariPool() {
        return dataSource instanceof HikariDataSource hikariDataSource
                ? hikariDataSource.getHikariPoolMXBean()
                : null;
    }

    private static double activeConnections(HikariDataSource dataSource) {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        return pool == null ? 0 : pool.getActiveConnections();
    }

    private static double idleConnections(HikariDataSource dataSource) {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        return pool == null ? 0 : pool.getIdleConnections();
    }

    private static double pendingConnections(HikariDataSource dataSource) {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        return pool == null ? 0 : pool.getThreadsAwaitingConnection();
    }

    private static String requestOutcome(
            boolean chainCompletedNormally,
            DashboardRequestMeasurements.Measurement measurement,
            int status) {
        if (!chainCompletedNormally) {
            return "unhandled_error";
        }
        if (!measurement.wasHandlerInvoked()) {
            return "short_circuit";
        }
        if (measurement.didHandlerFail() || status >= HttpServletResponse.SC_BAD_REQUEST) {
            return "http_error";
        }
        return "completed";
    }

    private static boolean isDashboardRoute(String route) {
        return route.startsWith("/api/restaurants/{restaurantId}/dashboard/");
    }

    private static String endpointType(String route) {
        return switch (route) {
            case NOTIFICATION_NEXT_ROUTE -> "payment_notification_next";
            case "/api/announcements/active" -> "announcement_active";
            case "/api/auth/me" -> "auth_me";
            case "/api/subscriptions/status" -> "subscription_status";
            default -> "dashboard";
        };
    }

    private static double millis(long nanos) {
        return DashboardRequestMeasurements.Measurement.toMillis(nanos);
    }

    private static String routeFor(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        if ("/api/announcements/active".equals(path)
                || "/api/auth/me".equals(path)
                || "/api/subscriptions/status".equals(path)) {
            return path;
        }

        String[] parts = path.split("/");
        if (parts.length == 6 && "api".equals(parts[1])
                && "restaurants".equals(parts[2]) && parts[3].matches("\\d+")
                && "dashboard".equals(parts[4])) {
            return switch (parts[5]) {
                case "summary" -> "/api/restaurants/{restaurantId}/dashboard/summary";
                case "recent-orders" -> "/api/restaurants/{restaurantId}/dashboard/recent-orders";
                case "active-orders" -> "/api/restaurants/{restaurantId}/dashboard/active-orders";
                default -> null;
            };
        }
        if (parts.length == 7 && "api".equals(parts[1])
                && "restaurants".equals(parts[2]) && parts[3].matches("\\d+")
                && "payments".equals(parts[4]) && "notifications".equals(parts[5])
                && "next".equals(parts[6])) {
            return NOTIFICATION_NEXT_ROUTE;
        }
        return null;
    }
}
