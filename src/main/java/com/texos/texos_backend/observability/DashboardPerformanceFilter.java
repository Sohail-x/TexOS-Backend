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

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;

    public DashboardPerformanceFilter(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        registerPoolGauges();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return routeFor(request) == null;
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

        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                measurement.finishSecurityIfNeeded();
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
                recordTimer("texos.dashboard.request", route, response.getStatus(), totalNanos);
                recordTimer("texos.dashboard.security", route, response.getStatus(), securityNanos);
                recordTimer("texos.dashboard.controller", route, response.getStatus(), controllerNanos);
                recordTimer("texos.dashboard.mvc.handler",
                        route, response.getStatus(), mvcHandlerNanos);
                recordTimer("texos.dashboard.service", route, response.getStatus(), serviceNanos);
                recordTimer("texos.dashboard.hibernate.query.execution",
                        route, response.getStatus(), queryExecutionNanos);
                recordTimer("texos.dashboard.hibernate.connection.acquisition",
                        route, response.getStatus(), connectionAcquisitionNanos);
                recordTimer("texos.dashboard.response.body.write",
                        route, response.getStatus(), serializationNanos);
                DistributionSummary.builder("texos.dashboard.hibernate.query.count")
                        .tag("route", route)
                        .register(meterRegistry)
                        .record(queryCount);

                HikariPoolMXBean pool = hikariPool();
                log.atInfo()
                        .addKeyValue("event", "dashboard_request")
                        .addKeyValue("route", route)
                        .addKeyValue("status", response.getStatus())
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
                        .addKeyValue("hikari_pending", pool == null ? -1 : pool.getThreadsAwaitingConnection())
                        .addKeyValue("application_uptime_ms",
                                ManagementFactory.getRuntimeMXBean().getUptime())
                        .log("dashboard_request");
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

    private void recordTimer(String name, String route, int status, long nanos) {
        Timer.Builder timer = Timer.builder(name)
                .tag("route", route)
                .tag("status", Integer.toString(status));
        if ("texos.dashboard.request".equals(name)) {
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

    private static double millis(long nanos) {
        return DashboardRequestMeasurements.Measurement.toMillis(nanos);
    }

    private static String routeFor(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        String[] parts = path.split("/");
        if (parts.length != 6 || !"api".equals(parts[1])
                || !"restaurants".equals(parts[2]) || !parts[3].matches("\\d+")
                || !"dashboard".equals(parts[4])) {
            return null;
        }
        return switch (parts[5]) {
            case "summary" -> "/api/restaurants/{restaurantId}/dashboard/summary";
            case "recent-orders" -> "/api/restaurants/{restaurantId}/dashboard/recent-orders";
            case "active-orders" -> "/api/restaurants/{restaurantId}/dashboard/active-orders";
            default -> null;
        };
    }
}
