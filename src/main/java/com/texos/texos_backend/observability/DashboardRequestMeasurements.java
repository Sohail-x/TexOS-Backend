package com.texos.texos_backend.observability;

import java.util.concurrent.TimeUnit;

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

    public static long startService() {
        return current() == null ? 0 : System.nanoTime();
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
        private final String route;
        private final long startedAtNanos;
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
        private long serializationStartedAtNanos;
        private boolean handlerInvoked;
        private boolean handlerFailed;
        private String notificationKind;

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

        public int getResponseOrderCount() {
            return responseOrderCount;
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

        public void startSerialization() {
            serializationStartedAtNanos = System.nanoTime();
        }

        public long getSerializationNanos() {
            return serializationStartedAtNanos == 0
                    ? 0
                    : System.nanoTime() - serializationStartedAtNanos;
        }

        public static double toMillis(long nanos) {
            return TimeUnit.NANOSECONDS.toMicros(nanos) / 1000.0;
        }
    }
}
