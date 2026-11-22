package com.texos.texos_backend.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.service.SubscriptionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RestaurantAccessFilter extends OncePerRequestFilter {

    private final OwnerRepository ownerRepository;
    private final RestaurantRepository restaurantRepository;
    private final SubscriptionService subscriptionService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String prefix = request.getContextPath() + "/api/restaurants/";

        if (!uri.startsWith(prefix)) {
            chain.doFilter(request, response);
            return;
        }

        String remainder = uri.substring(prefix.length());
        String firstSegment = remainder.split("/", 2)[0];

        if (!firstSegment.matches("\\d+")) {
            if ("owner".equals(firstSegment)) {
                Authentication authentication =
                        SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()
                        || "anonymousUser".equals(authentication.getPrincipal())) {
                    writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
                    return;
                }
                Owner owner = ownerRepository.findByEmail(authentication.getName())
                        .or(() -> ownerRepository.findByPhone(authentication.getName())).orElse(null);
                String[] parts = remainder.split("/", 3);
                if (owner == null || parts.length < 2 || !parts[1].matches("\\d+")) {
                    writeError(response, HttpStatus.FORBIDDEN,
                            "You do not have access to this owner's restaurants");
                    return;
                }
                long requestedOwnerId;
                try {
                    requestedOwnerId = Long.parseLong(parts[1]);
                } catch (NumberFormatException ex) {
                    writeError(response, HttpStatus.BAD_REQUEST, "Invalid owner ID");
                    return;
                }
                if (!owner.getId().equals(requestedOwnerId)) {
                    writeError(response, HttpStatus.FORBIDDEN,
                            "You do not have access to this owner's restaurants");
                    return;
                }
                if (!subscriptionService.hasAnyValidSubscriptionForOwner(owner.getId())) {
                    writeError(response, HttpStatus.FORBIDDEN,
                            "An active or trialing subscription is required to access this resource");
                    return;
                }
            }
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        Long restaurantId;
        try {
            restaurantId = Long.valueOf(firstSegment);
        } catch (NumberFormatException ex) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid restaurant ID");
            return;
        }

        Owner owner = ownerRepository.findByEmail(authentication.getName())
                .or(() -> ownerRepository.findByPhone(authentication.getName())).orElse(null);
        if (owner == null || !owner.isActive()
                || restaurantRepository.findByIdAndOwnerId(restaurantId, owner.getId()).isEmpty()) {
            writeError(response, HttpStatus.FORBIDDEN, "You do not have access to this restaurant");
            return;
        }
        if (!subscriptionService.hasValidSubscription(restaurantId)) {
            writeError(response, HttpStatus.FORBIDDEN,
                    "An active or trialing subscription is required to access this resource");
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
