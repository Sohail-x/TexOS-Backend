
package com.texos.texos_backend.config;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.service.AdminCredentials;

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
            String email = jwtService.extractEmail(token);
            String role = jwtService.extractRole(token);

            if (email != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

                if ("TEXOS_ADMIN".equals(role)
                        && adminCredentials.isUsername(email)
                        && jwtService.isAdminTokenValid(token, email)) {
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

                Owner owner = ownerRepository.findByEmail(email.trim().toLowerCase())
                    .or(() -> ownerRepository.findByPhone(email))
                    .orElse(null);

                if ((role == null || "OWNER".equals(role))
                    && owner != null &&
                    owner.isActive() &&
                    jwtService.isTokenValid(token, owner.getEmail() == null
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
                }
            }

        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
