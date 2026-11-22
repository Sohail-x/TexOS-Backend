package com.texos.texos_backend.config;

import com.texos.texos_backend.service.KitchenAccessService;
import com.texos.texos_backend.service.KitchenStaffPrincipal;
import com.texos.texos_backend.service.JwtService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class KitchenStaffAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final KitchenAccessService kitchenAccessService;

    public KitchenStaffAuthenticationFilter(
            JwtService jwtService,
            KitchenAccessService kitchenAccessService) {
        this.jwtService = jwtService;
        this.kitchenAccessService = kitchenAccessService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/kitchen-staff/");
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
                if ("KITCHEN_STAFF".equals(jwtService.extractRole(token))) {
                    String sessionValue = jwtService.extractKitchenStaffSessionId(token);
                    Long restaurantId = jwtService.extractKitchenStaffRestaurantId(token);
                    if (sessionValue != null && restaurantId != null) {
                        UUID sessionId = UUID.fromString(sessionValue);
                        if (kitchenAccessService.findActiveSession(sessionId, restaurantId) != null) {
                            KitchenStaffPrincipal principal =
                                    new KitchenStaffPrincipal(restaurantId, sessionId);
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_KITCHEN_STAFF")));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
