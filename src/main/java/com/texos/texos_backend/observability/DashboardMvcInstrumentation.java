package com.texos.texos_backend.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DashboardMvcInstrumentation implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DashboardHandlerTimingInterceptor())
                .addPathPatterns("/api/restaurants/*/dashboard/summary",
                        "/api/restaurants/*/dashboard/recent-orders",
                        "/api/restaurants/*/dashboard/active-orders",
                        "/api/restaurants/*/payments/notifications/next",
                        "/api/restaurants/*/loyalty/**",
                        "/api/announcements/active",
                        "/api/auth/me",
                        "/api/subscriptions/status");
    }

    private static class DashboardHandlerTimingInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(
                HttpServletRequest request,
                HttpServletResponse response,
                Object handler) {
            DashboardRequestMeasurements.Measurement measurement =
                    DashboardPerformanceFilter.currentMeasurement(request);
            if (measurement != null) {
                measurement.startMvcHandler();
            }
            return true;
        }

        @Override
        public void afterCompletion(
                HttpServletRequest request,
                HttpServletResponse response,
                Object handler,
                @Nullable Exception exception) {
            DashboardRequestMeasurements.Measurement measurement =
                    DashboardPerformanceFilter.currentMeasurement(request);
            if (measurement != null) {
                if (exception != null) {
                    measurement.recordHandlerFailure();
                }
                measurement.finishMvcHandler();
            }
        }
    }
}
