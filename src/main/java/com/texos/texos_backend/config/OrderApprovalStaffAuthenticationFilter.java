package com.texos.texos_backend.config;

import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.service.OrderApprovalService;
import com.texos.texos_backend.service.OrderApprovalStaffPrincipal;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OrderApprovalStaffAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final OrderApprovalService orderApprovalService;

    public OrderApprovalStaffAuthenticationFilter(
            JwtService jwtService, OrderApprovalService orderApprovalService) {
        this.jwtService = jwtService;
        this.orderApprovalService = orderApprovalService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI()
                .startsWith(request.getContextPath() + "/api/order-approval/staff/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String token = header.substring(7);
                if ("ORDER_APPROVER".equals(jwtService.extractRole(token))) {
                    UUID sessionId = UUID.fromString(jwtService.extractOrderApprovalSessionId(token));
                    Long restaurantId = jwtService.extractOrderApprovalRestaurantId(token);
                    if (restaurantId != null
                            && orderApprovalService.findActiveSession(sessionId, restaurantId) != null) {
                        OrderApprovalStaffPrincipal principal =
                                new OrderApprovalStaffPrincipal(restaurantId, sessionId);
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_ORDER_APPROVER")));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
