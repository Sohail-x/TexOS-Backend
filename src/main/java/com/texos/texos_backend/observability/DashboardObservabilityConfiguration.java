package com.texos.texos_backend.observability;

import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class DashboardObservabilityConfiguration {

    @Bean
    public DashboardPerformanceFilter dashboardPerformanceFilter(
            MeterRegistry meterRegistry,
            DataSource dataSource) {
        return new DashboardPerformanceFilter(meterRegistry, dataSource);
    }

    @Bean
    public FilterRegistrationBean<DashboardPerformanceFilter> dashboardPerformanceFilterRegistration(
            DashboardPerformanceFilter filter) {
        FilterRegistrationBean<DashboardPerformanceFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(true);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public DashboardSecurityTimingFilter dashboardSecurityTimingFilter() {
        return new DashboardSecurityTimingFilter();
    }

    @Bean
    public FilterRegistrationBean<DashboardSecurityTimingFilter>
            dashboardSecurityTimingFilterRegistration(DashboardSecurityTimingFilter filter) {
        FilterRegistrationBean<DashboardSecurityTimingFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
