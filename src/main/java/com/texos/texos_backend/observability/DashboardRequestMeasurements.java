package com.texos.texos_backend.observability;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class DashboardRequestMeasurements {

    private static final ThreadLocal<Measurement> CURRENT = new ThreadLocal<>();

    private DashboardRequestMeasurements() {}

    public static Measurement current() {
        return CURRENT.get();
    }

    public static Measurement begin(String route) {
        Measurement measurement = new Measurement(route, System.nanoTime());
        CURRENT.set(measurement);
        return measurement;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void recordServiceDuration(long startedAtNanos) {
        Measurement measurement = current();
        if (measurement != null && startedAtNanos != 0) {
            measurement.serviceNanos += System.nanoTime() - startedAtNanos;
        }
    }

    public static void recordResponseOrderCount(int count) {
        Measurement measurement = current();
        if (measurement != null) {
            measurement.recordResponseOrderCount(count);
        }
    }

    public static void recordOwnerResponseCounts(
            long records, long rewards, long redemptions, long auditLogs, long transactions) {
        Measurement measurement = current();
        if (measurement != null) {
            measurement.recordOwnerResponseCounts(records, rewards, redemptions, auditLogs, transactions);
        }
    }

    public static void recordRewardImagePayload(
            long imageCount, long dataUrlCharacters, long externalUrlCharacters) {
        Measurement measurement = current();
        if (measurement != null) {
            measurement.recordRewardImagePayload(
                    imageCount, dataUrlCharacters, externalUrlCharacters);
        }
    }

    public static void recordRepositoryResultCount(Measurement.Stage stage, long count) {
        Measurement measurement = current();
        if (measurement != null) {
            measurement.recordRepositoryResultCount(stage, count);
        }
    }

    public static void recordOrderPayloadCounts(
            int orderCount, long itemCount, long billItemCount) {
        Measurement measurement = current();
        if (measurement != null) {
            measurement.recordOrderPayloadCounts(orderCount, itemCount, billItemCount);
        }
    }

    public static long startService() {
        return current() == null ? 0 : System.nanoTime();
    }

    public static long startStage() {
        return current() == null ? 0 : System.nanoTime();
    }

    public static void recordStageDuration(
            Measurement.Stage stage, long startedAtNanos) {
        Measurement measurement = current();
        if (measurement != null && startedAtNanos != 0) {
            measurement.recordStageDuration(stage, System.nanoTime() - startedAtNanos);
        }
    }

    public static <T> T measureRepositoryOperation(
            Measurement.Stage stage, Supplier<T> operation) {
        Measurement measurement = current();
        if (measurement == null) {
            return operation.get();
        }
        long startedAtNanos = System.nanoTime();
        try {
            return operation.get();
        } finally {
            measurement.recordRepositoryOperation(
                    stage, System.nanoTime() - startedAtNanos);
        }
    }

    public static long startControllerTiming() {
        Measurement measurement = current();
        return measurement == null ? 0 : measurement.startControllerMethod();
    }

    public static void finishControllerTiming(long startedAtNanos) {
        Measurement measurement = current();
        if (measurement != null && startedAtNanos != 0) {
            measurement.finishControllerMethod(startedAtNanos);
        }
    }

    public static final class Measurement {
        public enum Stage {
            JWT_OWNER_LOOKUP("jwt_owner_lookup"),
            RESTAURANT_OWNERSHIP_VALIDATION("restaurant_ownership_validation"),
            SUBSCRIPTION_VALIDATION("subscription_validation"),
            SERVICE_OWNERSHIP_VALIDATION("service_ownership_validation"),
            PENDING_NOTIFICATION_QUERY("pending_notification_query"),
            RECENT_INITIAL_ORDER_REPOSITORY("recent_initial_order_repository_call"),
            RECENT_DETAIL_ORDER_REPOSITORY("recent_detail_order_repository_call"),
            RECENT_BILL_REPOSITORY("recent_bill_repository_call"),
            RECENT_DTO_MAPPING("recent_dto_mapping"),
            ACTIVE_ORDERS_REPOSITORY("active_orders_repository_call"),
            ACTIVE_BILLS_REPOSITORY("active_bills_repository_call"),
            ACTIVE_DTO_MAPPING("active_dto_mapping"),
            SUMMARY_IDENTITY_REPOSITORY("summary_identity_repository_call"),
            SUMMARY_AGGREGATE_REPOSITORY("summary_aggregate_repository_call"),
            SUMMARY_MAPPING("summary_mapping"),
            SERVICE_OWNER_REPOSITORY("service_owner_repository", true),
            SERVICE_RESTAURANT_REPOSITORY("service_restaurant_repository", true),
            LOYALTY_PROGRAM_REPOSITORY("loyalty_program_repository", true),
            LOYALTY_PROGRAM_SAVE("loyalty_program_save", true),
            OWNER_SETTINGS_SAVE("owner_settings_save", true),
            OWNER_REWARDS_LIST("owner_rewards_repository_call", true),
            OWNER_REDEMPTIONS_LIST("owner_redemptions_repository_call", true),
            OWNER_AUDIT_LOGS_LIST("owner_audit_logs_repository_call", true),
            OWNER_OVERVIEW_ACCOUNT_AGGREGATE("owner_overview_account_aggregate", true),
            OWNER_OVERVIEW_TRANSACTION_AGGREGATE("owner_overview_transaction_aggregate", true),
            OWNER_OVERVIEW_REWARD_COUNT("owner_overview_reward_count", true),
            OWNER_OVERVIEW_REDEMPTION_COUNT("owner_overview_redemption_count", true),
            OWNER_OVERVIEW_RECENT_REDEMPTIONS("owner_overview_recent_redemptions", true),
            OWNER_OVERVIEW_RECENT_TRANSACTIONS("owner_overview_recent_transactions", true),
            OWNER_OVERVIEW_REWARDS("owner_overview_rewards", true),
            OWNER_OVERVIEW_EARNED_TRANSACTIONS("owner_overview_earned_transactions", true),
            OWNER_OVERVIEW_REDEEMED_TRANSACTIONS("owner_overview_redeemed_transactions", true),
            OWNER_REWARD_LOCK("owner_reward_lock", true),
            OWNER_MENU_ITEM_LOOKUP("owner_menu_item_lookup", true),
            OWNER_REWARD_SAVE("owner_reward_save", true),
            OWNER_AUDIT_LOG_SAVE("owner_audit_log_save", true),
            OWNER_REWARD_MAPPING("owner_reward_mapping"),
            OWNER_REDEMPTION_MAPPING("owner_redemption_mapping"),
            OWNER_AUDIT_MAPPING("owner_audit_mapping"),
            OWNER_OVERVIEW_MAPPING("owner_overview_mapping"),
            OWNER_SETTINGS_MAPPING("owner_settings_mapping"),
            OWNER_REWARD_ASSOCIATION_LOADING("owner_reward_association_loading");

            private final String metricName;
            private final boolean repositoryOperation;

            Stage(String metricName) {
                this(metricName, false);
            }

            Stage(String metricName, boolean repositoryOperation) {
                this.metricName = metricName;
                this.repositoryOperation = repositoryOperation;
            }

            public String metricName() {
                return metricName;
            }

            public boolean isRepositoryOperation() {
                return repositoryOperation;
            }

            public boolean isOwnerRewardStage() {
                return metricName.startsWith("owner_")
                        || this == SERVICE_OWNER_REPOSITORY
                        || this == SERVICE_RESTAURANT_REPOSITORY
                        || this == LOYALTY_PROGRAM_REPOSITORY
                        || this == LOYALTY_PROGRAM_SAVE
                        || this == SERVICE_OWNERSHIP_VALIDATION;
            }
        }

        private final String route;
        private final long startedAtNanos;
        private final Map<Stage, Long> stageNanos = new EnumMap<>(Stage.class);
        private final Map<Stage, Long> repositoryResultCounts = new EnumMap<>(Stage.class);
        private long securityNanos;
        private boolean securityMeasured;
        private boolean securityFilterReached;
        private long controllerMethodNanos;
        private long mvcHandlerStartedAtNanos;
        private long mvcHandlerNanos;
        private long serviceNanos;
        private long queryExecutionNanos;
        private long connectionAcquisitionNanos;
        private long queryCount;
        private int responseOrderCount = -1;
        private long jsonConversionResponseStartedAtNanos;
        private long responseBodyBytes = -1;
        private long responseOutputWriteNanos;
        private boolean handlerInvoked;
        private boolean handlerFailed;
        private String notificationKind;
        private long responseItemCount = -1;
        private long responseBillItemCount = -1;
        private long responseRecordCount = -1;
        private long responseRewardCount = -1;
        private long responseRedemptionCount = -1;
        private long responseAuditLogCount = -1;
        private long responseTransactionCount = -1;
        private long rewardImageCount = -1;
        private long rewardImageDataUrlCharacters = -1;
        private long rewardImageExternalUrlCharacters = -1;
        private String slowestRepositoryOperation = "not_observed";
        private long slowestRepositoryOperationNanos = -1;

        private Measurement(String route, long startedAtNanos) {
            this.route = route;
            this.startedAtNanos = startedAtNanos;
            this.notificationKind = "/api/restaurants/{restaurantId}/payments/notifications/next"
                    .equals(route) ? "not_observed" : "not_applicable";
        }

        public String getRoute() {
            return route;
        }

        public long getElapsedNanos() {
            return System.nanoTime() - startedAtNanos;
        }

        public long getSecurityNanos() {
            return securityNanos;
        }

        public void finishSecurityIfNeeded() {
            if (!securityMeasured) {
                securityNanos = System.nanoTime() - startedAtNanos;
                securityMeasured = true;
            }
        }

        public void finishSecurityAtFilter() {
            if (!securityMeasured) {
                securityNanos = System.nanoTime() - startedAtNanos;
                securityMeasured = true;
                securityFilterReached = true;
            }
        }

        public boolean wasSecurityFilterReached() {
            return securityFilterReached;
        }

        public long startControllerMethod() {
            return System.nanoTime();
        }

        public void finishControllerMethod(long startedAtNanos) {
            if (startedAtNanos != 0) {
                controllerMethodNanos += System.nanoTime() - startedAtNanos;
            }
        }

        public void startMvcHandler() {
            handlerInvoked = true;
            mvcHandlerStartedAtNanos = System.nanoTime();
        }

        public void finishMvcHandler() {
            if (mvcHandlerStartedAtNanos != 0) {
                mvcHandlerNanos = System.nanoTime() - mvcHandlerStartedAtNanos;
            }
        }

        public void recordHandlerFailure() {
            handlerFailed = true;
        }

        public boolean wasHandlerInvoked() {
            return handlerInvoked;
        }

        public boolean didHandlerFail() {
            return handlerFailed;
        }

        public void recordNotificationType(Object notificationType) {
            if (!"/api/restaurants/{restaurantId}/payments/notifications/next".equals(route)) {
                return;
            }
            if (notificationType == null) {
                notificationKind = "empty";
            } else if ("PAYMENT_SUCCESS".equals(notificationType)) {
                notificationKind = "payment_notification";
            } else if ("CALL_WAITER".equals(notificationType)) {
                notificationKind = "waiter_notification";
            } else {
                notificationKind = "other_notification";
            }
        }

        public String getNotificationKind() {
            return notificationKind;
        }

        public long getControllerMethodNanos() {
            return controllerMethodNanos;
        }

        public long getMvcHandlerNanos() {
            return mvcHandlerNanos;
        }

        public long getServiceNanos() {
            return serviceNanos;
        }

        public long getQueryExecutionNanos() {
            return queryExecutionNanos;
        }

        public long getConnectionAcquisitionNanos() {
            return connectionAcquisitionNanos;
        }

        public long getQueryCount() {
            return queryCount;
        }

        public long getStageNanos(Stage stage) {
            return stageNanos.getOrDefault(stage, -1L);
        }

        public boolean hasStage(Stage stage) {
            return stageNanos.containsKey(stage);
        }

        public long getRepositoryResultCount(Stage stage) {
            return repositoryResultCounts.getOrDefault(stage, -1L);
        }

        public void recordStageDuration(Stage stage, long nanos) {
            stageNanos.merge(stage, nanos, Long::sum);
        }

        public void recordRepositoryResultCount(Stage stage, long count) {
            repositoryResultCounts.put(stage, count);
        }

        public void recordRepositoryOperation(Stage stage, long nanos) {
            recordStageDuration(stage, nanos);
            if (stage.isRepositoryOperation() && nanos > slowestRepositoryOperationNanos) {
                slowestRepositoryOperation = stage.metricName();
                slowestRepositoryOperationNanos = nanos;
            }
        }

        public int getResponseOrderCount() {
            return responseOrderCount;
        }

        public long getResponseItemCount() {
            return responseItemCount;
        }

        public long getResponseBillItemCount() {
            return responseBillItemCount;
        }

        public long getResponseRecordCount() {
            return responseRecordCount;
        }

        public long getResponseRewardCount() {
            return responseRewardCount;
        }

        public long getResponseRedemptionCount() {
            return responseRedemptionCount;
        }

        public long getResponseAuditLogCount() {
            return responseAuditLogCount;
        }

        public long getResponseTransactionCount() {
            return responseTransactionCount;
        }

        public long getRewardImageCount() {
            return rewardImageCount;
        }

        public long getRewardImageDataUrlCharacters() {
            return rewardImageDataUrlCharacters;
        }

        public long getRewardImageExternalUrlCharacters() {
            return rewardImageExternalUrlCharacters;
        }

        public String getSlowestRepositoryOperation() {
            return slowestRepositoryOperation;
        }

        public long getSlowestRepositoryOperationNanos() {
            return slowestRepositoryOperationNanos;
        }

        public void recordOrderPayloadCounts(
                int orderCount, long itemCount, long billItemCount) {
            responseOrderCount = orderCount;
            responseItemCount = itemCount;
            responseBillItemCount = billItemCount;
        }

        public void recordOwnerResponseCounts(
                long records, long rewards, long redemptions, long auditLogs, long transactions) {
            responseRecordCount = records;
            responseRewardCount = rewards;
            responseRedemptionCount = redemptions;
            responseAuditLogCount = auditLogs;
            responseTransactionCount = transactions;
        }

        public void recordRewardImagePayload(
                long imageCount, long dataUrlCharacters, long externalUrlCharacters) {
            rewardImageCount = imageCount;
            rewardImageDataUrlCharacters = dataUrlCharacters;
            rewardImageExternalUrlCharacters = externalUrlCharacters;
        }

        public long getResponseBodyBytes() {
            return responseBodyBytes;
        }

        public long getResponseOutputWriteNanos() {
            return responseOutputWriteNanos;
        }

        public void recordResponseOutput(long bytes, long writeNanos) {
            responseBodyBytes = bytes;
            responseOutputWriteNanos = writeNanos;
        }

        public void recordQuery() {
            queryCount++;
        }

        public void recordResponseOrderCount(int count) {
            responseOrderCount = count;
        }

        public void addQueryExecutionNanos(long nanos) {
            queryExecutionNanos += nanos;
        }

        public void addConnectionAcquisitionNanos(long nanos) {
            connectionAcquisitionNanos += nanos;
        }

        public void startJsonConversionResponseWindow() {
            jsonConversionResponseStartedAtNanos = System.nanoTime();
        }

        public long getJsonConversionResponseWindowNanos() {
            return jsonConversionResponseStartedAtNanos == 0
                    ? 0
                    : System.nanoTime() - jsonConversionResponseStartedAtNanos;
        }

        public static double toMillis(long nanos) {
            return TimeUnit.NANOSECONDS.toMicros(nanos) / 1000.0;
        }
    }
}
