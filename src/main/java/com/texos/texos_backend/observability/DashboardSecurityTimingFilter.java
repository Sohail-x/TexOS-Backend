package com.texos.texos_backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class DashboardSecurityTimingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return DashboardPerformanceFilter.currentMeasurement(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        DashboardRequestMeasurements.Measurement measurement =
                DashboardPerformanceFilter.currentMeasurement(request);
        if (measurement != null) {
            measurement.finishSecurityAtFilter();
        }
        filterChain.doFilter(request, response);
    }
}
