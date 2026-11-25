package com.texos.texos_backend.observability;

import com.texos.texos_backend.controller.DashboardController;
import com.texos.texos_backend.controller.AnnouncementController;
import com.texos.texos_backend.controller.AuthController;
import com.texos.texos_backend.controller.RestaurantPaymentController;
import com.texos.texos_backend.controller.SubscriptionController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice(assignableTypes = {
        DashboardController.class,
        RestaurantPaymentController.class,
        AnnouncementController.class,
        AuthController.class,
        SubscriptionController.class
})
public class DashboardResponseTimingAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        HttpServletRequest servletRequest =
                request instanceof ServletServerHttpRequest servletRequestWrapper
                ? servletRequestWrapper.getServletRequest()
                : null;
        if (servletRequest != null) {
            DashboardRequestMeasurements.Measurement measurement =
                    DashboardPerformanceFilter.currentMeasurement(servletRequest);
            if (measurement != null) {
                measurement.startJsonConversionResponseWindow();
            }
        }
        return body;
    }
}
