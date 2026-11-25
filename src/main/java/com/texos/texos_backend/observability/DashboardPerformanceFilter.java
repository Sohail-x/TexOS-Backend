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
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.filter.OncePerRequestFilter;

public class DashboardPerformanceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DashboardPerformanceFilter.class);
    private static final String REQUEST_ATTRIBUTE =
            DashboardPerformanceFilter.class.getName() + ".measurement";
    private static final String NOTIFICATION_NEXT_ROUTE =
            "/api/restaurants/{restaurantId}/payments/notifications/next";
    private static final String LOYALTY_ROUTE_PREFIX =
            "/api/restaurants/{restaurantId}/loyalty/";
    private static final String LOYALTY_REWARD_CREATE_ROUTE =
            LOYALTY_ROUTE_PREFIX + "reward/create";
    private static final String LOYALTY_REWARD_UPDATE_ROUTE =
            LOYALTY_ROUTE_PREFIX + "reward/update";
    private static final String LOYALTY_REWARD_STATUS_ROUTE =
            LOYALTY_ROUTE_PREFIX + "reward/status";
    private static final String LOYALTY_REWARD_ARCHIVE_ROUTE =
            LOYALTY_ROUTE_PREFIX + "reward/archive";
    private static final String LOYALTY_SETTINGS_UPDATE_ROUTE =
            LOYALTY_ROUTE_PREFIX + "settings/update";

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
        MeasuredHttpServletResponse measuredResponse = isMeasuredResponseRoute(route)
                ? new MeasuredHttpServletResponse(response)
                : null;
        HttpServletResponse instrumentedResponse =
                measuredResponse == null ? response : measuredResponse;
        request.setAttribute(REQUEST_ATTRIBUTE, measurement);
        boolean chainCompletedNormally = false;

        try {
            filterChain.doFilter(request, instrumentedResponse);
            chainCompletedNormally = true;
        } finally {
            try {
                if (request.isAsyncStarted()) {
                    logAsyncRequest(route, response.getStatus());
                } else {
                    try {
                        if (chainCompletedNormally && measuredResponse != null) {
                            measuredResponse.finishMeasurement();
                        }
                    } finally {
                        if (isDashboardRoute(route)) {
                            measurement.finishSecurityIfNeeded();
                        }
                        String outcome = requestOutcome(
                                chainCompletedNormally, measurement, response.getStatus());
                        if (measuredResponse != null) {
                            measurement.recordResponseOutput(
                                    measuredResponse.getBytesWritten(),
                                    measuredResponse.getOutputWriteNanos());
                        }
                        if (isDashboardRoute(route)) {
                            recordDashboardRequest(route, response, measurement);
                        } else {
                            recordApiRequest(route, response, measurement, outcome);
                        }
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
        long serializationNanos = measurement.getJsonConversionResponseWindowNanos();
        long responseBytes = measurement.getResponseBodyBytes();
        long responseOutputWriteNanos = measurement.getResponseOutputWriteNanos();
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
        recordDashboardTimer(
                "texos.dashboard.json.conversion.response.window",
                route, status, serializationNanos);
        if (responseBytes >= 0) {
            recordDashboardTimer(
                    "texos.dashboard.response.output.write",
                    route, status, responseOutputWriteNanos);
        }
        recordStageTimers(route, status, measurement);
        DistributionSummary.builder("texos.dashboard.hibernate.query.count")
                .tag("route", route)
                .register(meterRegistry)
                .record(queryCount);
        if (responseBytes >= 0) {
            DistributionSummary.builder("texos.dashboard.response.body.bytes")
                    .tag("route", route)
                    .tag("status", Integer.toString(status))
                    .register(meterRegistry)
                    .record(responseBytes);
        }

        HikariPoolMXBean pool = hikariPool();
        log.atInfo()
                .addKeyValue("event", "dashboard_request")
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("timing_relationship", "nested_non_additive")
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
                .addKeyValue("json_conversion_response_window_ms", millis(serializationNanos))
                .addKeyValue("response_output_write_call_ms",
                        responseBytes < 0 ? -1 : millis(responseOutputWriteNanos))
                .addKeyValue("response_body_bytes", responseBytes)
                .addKeyValue("hikari_active", pool == null ? -1 : pool.getActiveConnections())
                .addKeyValue("hikari_idle", pool == null ? -1 : pool.getIdleConnections())
                .addKeyValue("hikari_pending",
                        pool == null ? -1 : pool.getThreadsAwaitingConnection())
                .addKeyValue("application_uptime_ms",
                        ManagementFactory.getRuntimeMXBean().getUptime())
                .addKeyValue("response_item_count", measurement.getResponseItemCount())
                .addKeyValue("response_bill_item_count", measurement.getResponseBillItemCount())
                .addKeyValue("recent_initial_order_repository_call_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.RECENT_INITIAL_ORDER_REPOSITORY))
                .addKeyValue("recent_detail_order_repository_call_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.RECENT_DETAIL_ORDER_REPOSITORY))
                .addKeyValue("recent_bill_repository_call_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.RECENT_BILL_REPOSITORY))
                .addKeyValue("recent_dto_mapping_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.RECENT_DTO_MAPPING))
                .addKeyValue("active_orders_repository_call_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.ACTIVE_ORDERS_REPOSITORY))
                .addKeyValue("active_bills_repository_call_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.ACTIVE_BILLS_REPOSITORY))
                .addKeyValue("active_dto_mapping_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.ACTIVE_DTO_MAPPING))
                .addKeyValue("summary_identity_repository_call_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.SUMMARY_IDENTITY_REPOSITORY))
                .addKeyValue("summary_aggregate_repository_call_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.SUMMARY_AGGREGATE_REPOSITORY))
                .addKeyValue("summary_mapping_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.SUMMARY_MAPPING))
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
        long serializationNanos = measurement.getJsonConversionResponseWindowNanos();
        long responseBytes = measurement.getResponseBodyBytes();
        long responseOutputWriteNanos = measurement.getResponseOutputWriteNanos();
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
        recordApiTimer("texos.api.json.conversion.response.window",
                endpointType, status, outcome, serializationNanos);
        if (responseBytes >= 0) {
            recordApiTimer("texos.api.response.output.write",
                    endpointType, status, outcome, responseOutputWriteNanos);
        }
        recordStageTimers(endpointType, status, outcome, measurement);
        DistributionSummary.builder("texos.api.hibernate.query.count")
                .tag("endpoint_type", endpointType)
                .tag("status", Integer.toString(status))
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(queryCount);
        if (responseBytes >= 0) {
            DistributionSummary.builder("texos.api.response.body.bytes")
                    .tag("endpoint_type", endpointType)
                    .tag("status", Integer.toString(status))
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(responseBytes);
        }
        if (isOwnerRewardRoute(route)) {
            recordOwnerResponseSummaries(endpointType, status, outcome, measurement);
        }

        HikariPoolMXBean pool = hikariPool();
        LoggingEventBuilder event = log.atInfo()
                .addKeyValue("event", "api_request_performance")
                .addKeyValue("endpoint_type", endpointType)
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("outcome", outcome)
                .addKeyValue("timing_relationship", "nested_non_additive")
                .addKeyValue("request_total_ms", millis(totalNanos))
                .addKeyValue("endpoint_total_ms", millis(totalNanos))
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
                .addKeyValue("json_conversion_response_window_ms", millis(serializationNanos))
                .addKeyValue("response_output_write_call_ms",
                        responseBytes < 0 ? -1 : millis(responseOutputWriteNanos))
                .addKeyValue("response_body_bytes", responseBytes)
                .addKeyValue("notification_kind", measurement.getNotificationKind())
                .addKeyValue("jwt_owner_lookup_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.JWT_OWNER_LOOKUP))
                .addKeyValue("restaurant_ownership_validation_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.RESTAURANT_OWNERSHIP_VALIDATION))
                .addKeyValue("subscription_validation_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.SUBSCRIPTION_VALIDATION))
                .addKeyValue("service_ownership_validation_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.SERVICE_OWNERSHIP_VALIDATION))
                .addKeyValue("pending_notification_query_ms",
                        stageMillis(measurement, DashboardRequestMeasurements.Measurement.Stage.PENDING_NOTIFICATION_QUERY))
                .addKeyValue("hikari_active", pool == null ? -1 : pool.getActiveConnections())
                .addKeyValue("hikari_idle", pool == null ? -1 : pool.getIdleConnections())
                .addKeyValue("hikari_pending",
                        pool == null ? -1 : pool.getThreadsAwaitingConnection())
                .addKeyValue("application_uptime_ms",
                        ManagementFactory.getRuntimeMXBean().getUptime());
        if (isOwnerRewardRoute(route)) {
            addOwnerRewardLogFields(event, measurement);
        }
        event.log("api_request_performance");
    }

    private void recordOwnerResponseSummaries(
            String endpointType,
            int status,
            String outcome,
            DashboardRequestMeasurements.Measurement measurement) {
        recordApiSummary("texos.api.response.record.count", "records",
                endpointType, status, outcome, measurement.getResponseRecordCount());
        recordApiSummary("texos.api.response.record.count", "rewards",
                endpointType, status, outcome, measurement.getResponseRewardCount());
        recordApiSummary("texos.api.response.record.count", "redemptions",
                endpointType, status, outcome, measurement.getResponseRedemptionCount());
        recordApiSummary("texos.api.response.record.count", "audit_logs",
                endpointType, status, outcome, measurement.getResponseAuditLogCount());
        recordApiSummary("texos.api.response.record.count", "transactions",
                endpointType, status, outcome, measurement.getResponseTransactionCount());
        recordApiSummary("texos.api.reward.image.count", "images",
                endpointType, status, outcome, measurement.getRewardImageCount());
        recordApiSummary("texos.api.reward.image.characters", "data_url",
                endpointType, status, outcome, measurement.getRewardImageDataUrlCharacters());
        recordApiSummary("texos.api.reward.image.characters", "external_url",
                endpointType, status, outcome, measurement.getRewardImageExternalUrlCharacters());
        for (DashboardRequestMeasurements.Measurement.Stage stage
                : DashboardRequestMeasurements.Measurement.Stage.values()) {
            long resultCount = measurement.getRepositoryResultCount(stage);
            if (resultCount >= 0) {
                recordApiSummary("texos.api.repository.result.count", stage.metricName(),
                        endpointType, status, outcome, resultCount);
            }
        }
    }

    private void recordApiSummary(
            String name,
            String valueType,
            String endpointType,
            int status,
            String outcome,
            long value) {
        if (value < 0) {
            return;
        }
        DistributionSummary.builder(name)
                .tag("value_type", valueType)
                .tag("endpoint_type", endpointType)
                .tag("status", Integer.toString(status))
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(value);
    }

    private static void addOwnerRewardLogFields(
            LoggingEventBuilder event,
            DashboardRequestMeasurements.Measurement measurement) {
        event.addKeyValue("response_record_count", measurement.getResponseRecordCount())
                .addKeyValue("response_reward_count", measurement.getResponseRewardCount())
                .addKeyValue("response_redemption_count",
                        measurement.getResponseRedemptionCount())
                .addKeyValue("response_audit_log_count", measurement.getResponseAuditLogCount())
                .addKeyValue("response_transaction_count",
                        measurement.getResponseTransactionCount())
                .addKeyValue("reward_image_count", measurement.getRewardImageCount())
                .addKeyValue("reward_image_data_url_characters",
                        measurement.getRewardImageDataUrlCharacters())
                .addKeyValue("reward_image_external_url_characters",
                        measurement.getRewardImageExternalUrlCharacters())
                .addKeyValue("slowest_repository_operation",
                        measurement.getSlowestRepositoryOperation())
                .addKeyValue("slowest_repository_operation_ms",
                        measurement.getSlowestRepositoryOperationNanos() < 0
                                ? -1
                                : millis(measurement.getSlowestRepositoryOperationNanos()));
        for (DashboardRequestMeasurements.Measurement.Stage stage
                : DashboardRequestMeasurements.Measurement.Stage.values()) {
            if (stage.isOwnerRewardStage()) {
                event.addKeyValue(stage.metricName() + "_ms", stageMillis(measurement, stage));
                long resultCount = measurement.getRepositoryResultCount(stage);
                if (resultCount >= 0) {
                    event.addKeyValue(stage.metricName() + "_records", resultCount);
                }
            }
        }
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

    private void recordStageTimers(
            String route,
            int status,
            DashboardRequestMeasurements.Measurement measurement) {
        for (DashboardRequestMeasurements.Measurement.Stage stage
                : DashboardRequestMeasurements.Measurement.Stage.values()) {
            long nanos = measurement.getStageNanos(stage);
            if (nanos >= 0) {
                Timer.builder("texos.dashboard.stage.duration")
                        .tag("route", route)
                        .tag("stage", stage.metricName())
                        .tag("status", Integer.toString(status))
                        .register(meterRegistry)
                        .record(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    private void recordStageTimers(
            String endpointType,
            int status,
            String outcome,
            DashboardRequestMeasurements.Measurement measurement) {
        for (DashboardRequestMeasurements.Measurement.Stage stage
                : DashboardRequestMeasurements.Measurement.Stage.values()) {
            long nanos = measurement.getStageNanos(stage);
            if (nanos >= 0) {
                Timer.builder("texos.api.stage.duration")
                        .tag("endpoint_type", endpointType)
                        .tag("stage", stage.metricName())
                        .tag("status", Integer.toString(status))
                        .tag("outcome", outcome)
                        .register(meterRegistry)
                        .record(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    private static double stageMillis(
            DashboardRequestMeasurements.Measurement measurement,
            DashboardRequestMeasurements.Measurement.Stage stage) {
        long nanos = measurement.getStageNanos(stage);
        return nanos < 0 ? -1 : millis(nanos);
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

    private static boolean isOrderDashboardRoute(String route) {
        return route.endsWith("/dashboard/recent-orders")
                || route.endsWith("/dashboard/active-orders");
    }

    private static boolean isOwnerRewardRoute(String route) {
        return route.startsWith(LOYALTY_ROUTE_PREFIX);
    }

    private static boolean isMeasuredResponseRoute(String route) {
        return isOrderDashboardRoute(route) || isOwnerRewardRoute(route);
    }

    private static String endpointType(String route) {
        return switch (route) {
            case NOTIFICATION_NEXT_ROUTE -> "payment_notification_next";
            case "/api/announcements/active" -> "announcement_active";
            case "/api/auth/me" -> "auth_me";
            case "/api/subscriptions/status" -> "subscription_status";
            case "/api/restaurants/{restaurantId}/loyalty/overview" -> "owner_loyalty_overview";
            case "/api/restaurants/{restaurantId}/loyalty/settings" -> "owner_loyalty_settings";
            case LOYALTY_SETTINGS_UPDATE_ROUTE -> "owner_loyalty_settings_update";
            case "/api/restaurants/{restaurantId}/loyalty/rewards" -> "owner_loyalty_rewards";
            case LOYALTY_REWARD_CREATE_ROUTE -> "owner_loyalty_reward_create";
            case LOYALTY_REWARD_UPDATE_ROUTE -> "owner_loyalty_reward_update";
            case LOYALTY_REWARD_STATUS_ROUTE -> "owner_loyalty_reward_status";
            case LOYALTY_REWARD_ARCHIVE_ROUTE -> "owner_loyalty_reward_archive";
            case "/api/restaurants/{restaurantId}/loyalty/redemptions" -> "owner_loyalty_redemptions";
            case "/api/restaurants/{restaurantId}/loyalty/audit-logs" -> "owner_loyalty_audit_logs";
            default -> "dashboard";
        };
    }

    private static double millis(long nanos) {
        return DashboardRequestMeasurements.Measurement.toMillis(nanos);
    }

    private static String routeFor(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        if ("GET".equalsIgnoreCase(method)
                && ("/api/announcements/active".equals(path)
                || "/api/auth/me".equals(path)
                || "/api/subscriptions/status".equals(path))) {
            return path;
        }

        String[] parts = path.split("/");
        if (parts.length >= 5 && "api".equals(parts[1])
                && "restaurants".equals(parts[2]) && parts[3].matches("\\d+")) {
            if (parts.length == 6 && "GET".equalsIgnoreCase(method)
                    && "dashboard".equals(parts[4])) {
                return switch (parts[5]) {
                    case "summary" -> "/api/restaurants/{restaurantId}/dashboard/summary";
                    case "recent-orders" -> "/api/restaurants/{restaurantId}/dashboard/recent-orders";
                    case "active-orders" -> "/api/restaurants/{restaurantId}/dashboard/active-orders";
                    default -> null;
                };
            }
            if (parts.length >= 6 && "loyalty".equals(parts[4])) {
                return loyaltyRoute(method, parts);
            }
        }
        if ("GET".equalsIgnoreCase(method) && parts.length == 7 && "api".equals(parts[1])
                && "restaurants".equals(parts[2]) && parts[3].matches("\\d+")
                && "payments".equals(parts[4]) && "notifications".equals(parts[5])
                && "next".equals(parts[6])) {
            return NOTIFICATION_NEXT_ROUTE;
        }
        return null;
    }

    private static String loyaltyRoute(String method, String[] parts) {
        if (parts.length == 6) {
            return switch (parts[5]) {
                case "overview" -> "GET".equalsIgnoreCase(method)
                        ? LOYALTY_ROUTE_PREFIX + "overview" : null;
                case "settings" -> "GET".equalsIgnoreCase(method)
                        ? LOYALTY_ROUTE_PREFIX + "settings"
                        : "PUT".equalsIgnoreCase(method) ? LOYALTY_SETTINGS_UPDATE_ROUTE : null;
                case "rewards" -> "GET".equalsIgnoreCase(method)
                        ? LOYALTY_ROUTE_PREFIX + "rewards"
                        : "POST".equalsIgnoreCase(method)
                                ? LOYALTY_REWARD_CREATE_ROUTE : null;
                case "redemptions" -> "GET".equalsIgnoreCase(method)
                        ? LOYALTY_ROUTE_PREFIX + "redemptions" : null;
                case "audit-logs" -> "GET".equalsIgnoreCase(method)
                        ? LOYALTY_ROUTE_PREFIX + "audit-logs" : null;
                default -> null;
            };
        }
        if (parts.length == 7 && "rewards".equals(parts[5]) && parts[6].matches("\\d+")
                && "PUT".equalsIgnoreCase(method)) {
            return LOYALTY_REWARD_UPDATE_ROUTE;
        }
        if (parts.length == 8 && "rewards".equals(parts[5]) && parts[6].matches("\\d+")
                && "status".equals(parts[7]) && "PATCH".equalsIgnoreCase(method)) {
            return LOYALTY_REWARD_STATUS_ROUTE;
        }
        if (parts.length == 7 && "rewards".equals(parts[5]) && parts[6].matches("\\d+")
                && "DELETE".equalsIgnoreCase(method)) {
            return LOYALTY_REWARD_ARCHIVE_ROUTE;
        }
        return null;
    }
}
