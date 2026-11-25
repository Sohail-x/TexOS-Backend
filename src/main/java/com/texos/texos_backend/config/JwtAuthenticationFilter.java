package com.texos.texos_backend.config;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.OwnerRepository.OwnerAuthView;
import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.service.AdminCredentials;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;
import com.texos.texos_backend.observability.DashboardRequestMeasurements.Measurement.Stage;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final OwnerRepository ownerRepository;
    private final AdminCredentials adminCredentials;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            JwtService.VerifiedTokenClaims claims =
                    jwtService.parseAuthenticationToken(token);
            String email = claims.subject();
            String role = claims.role();

            if (email != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

                if ("TEXOS_ADMIN".equals(role)
                        && adminCredentials.isUsername(email)
                        && jwtService.isAdminTokenValid(claims, email)) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            java.util.List.of(
                                new SimpleGrantedAuthority("ROLE_TEXOS_ADMIN")
                            )
                        );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    filterChain.doFilter(request, response);
                    return;
                }

                long ownerLookupStartedAtNanos = DashboardRequestMeasurements.startStage();
                OwnerAuthView owner;
                try {
                    owner = ownerRepository.findAuthViewByIdentity(email.trim()).orElse(null);
                } finally {
                    DashboardRequestMeasurements.recordStageDuration(
                            Stage.JWT_OWNER_LOOKUP, ownerLookupStartedAtNanos);
                }

                if ((role == null || "OWNER".equals(role))
                    && owner != null &&
                    owner.isActive() &&
                    jwtService.isTokenValid(claims, owner.getEmail() == null
                            || owner.getEmail().isBlank() ? owner.getPhone() : owner.getEmail(),
                            owner.getTokenVersion())) {

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            java.util.List.of(
                                new SimpleGrantedAuthority("ROLE_OWNER")
                            )
                        );

                    SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
                    request.setAttribute(
                            AuthenticatedOwnerPrincipal.REQUEST_ATTRIBUTE,
                            new AuthenticatedOwnerPrincipal(owner.getId()));
                }
            }

        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
