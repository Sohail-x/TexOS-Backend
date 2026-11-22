package com.texos.texos_backend.config;

import java.util.Arrays;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Value("${app.frontend-origin}")
    private String frontendOrigin;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("TexOS uses token-based authentication.");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                frontendOrigin.replaceAll("/+$", "")));
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // Customer QR login uses the browser session cookie across the Vite/API origins.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Prevent Spring Boot from registering these filters twice as servlet filters.
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RestaurantAccessFilter> restaurantAccessFilterRegistration(
            RestaurantAccessFilter filter) {
        FilterRegistrationBean<RestaurantAccessFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<KitchenStaffAuthenticationFilter>
            kitchenStaffAuthenticationFilterRegistration(KitchenStaffAuthenticationFilter filter) {
        FilterRegistrationBean<KitchenStaffAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<OrderApprovalStaffAuthenticationFilter>
            orderApprovalStaffAuthenticationFilterRegistration(
                    OrderApprovalStaffAuthenticationFilter filter) {
        FilterRegistrationBean<OrderApprovalStaffAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthRateLimitFilter authRateLimitFilter,
            KitchenStaffAuthenticationFilter kitchenStaffAuthenticationFilter,
            OrderApprovalStaffAuthenticationFilter orderApprovalStaffAuthenticationFilter,
            RestaurantAccessFilter restaurantAccessFilter) throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jsonAuthenticationEntryPoint()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/owners/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/public/contact").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/google").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/kitchen-staff/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/public/order-approval/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/public/order-approval/access").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/order-approval/push-config").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/subscriptions/plans").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/webhooks/phonepe").permitAll()
                .requestMatchers("/api/admin/**").hasRole("TEXOS_ADMIN")
                .requestMatchers("/api/kitchen-staff/**").hasRole("KITCHEN_STAFF")
                .requestMatchers("/api/order-approval/staff/**").hasRole("ORDER_APPROVER")
                .requestMatchers("/api/subscriptions/**").hasRole("OWNER")
                .requestMatchers("/api/announcements/**").hasRole("OWNER")
                .requestMatchers("/api/public/customers/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/menu/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/loyalty/status").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/loyalty/balance").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/public/orders").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/public/orders/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/orders/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                // Every owner/restaurant management API requires a valid JWT.
                .requestMatchers("/api/restaurants/**").hasRole("OWNER")
                .requestMatchers("/api/auth/**").hasRole("OWNER")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(authRateLimitFilter, JwtAuthenticationFilter.class)
            .addFilterAfter(kitchenStaffAuthenticationFilter, JwtAuthenticationFilter.class)
            .addFilterAfter(orderApprovalStaffAuthenticationFilter, KitchenStaffAuthenticationFilter.class)
            .addFilterAfter(restaurantAccessFilter, OrderApprovalStaffAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\"}");
        };
    }
}
