package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.AccountDeletionRequest;
import com.texos.texos_backend.entity.RestaurantTable;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.repository.RestaurantTableRepository;
import com.texos.texos_backend.service.CustomerAuthService;
import com.texos.texos_backend.service.CustomerSession;
import com.texos.texos_backend.service.CustomerSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/auth")
public class CustomerAccountController {

    private final CustomerAuthService customerAuthService;
    private final CustomerSessionService customerSessionService;
    private final RestaurantTableRepository tableRepository;
    private final String sessionCookieSameSite;

    public CustomerAccountController(
            CustomerAuthService customerAuthService,
            CustomerSessionService customerSessionService,
            RestaurantTableRepository tableRepository,
            @Value("${server.servlet.session.cookie.same-site}") String sessionCookieSameSite) {
        this.customerAuthService = customerAuthService;
        this.customerSessionService = customerSessionService;
        this.tableRepository = tableRepository;
        this.sessionCookieSameSite = sessionCookieSameSite;
    }

    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteAccount(
            @RequestParam String qrToken,
            @Valid @RequestBody AccountDeletionRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (!"DELETE".equals(request.confirmation())) {
            throw new InvalidRequestException("Type DELETE to confirm account deletion.");
        }

        RestaurantTable table = tableRepository.findByQrTokenAndActiveTrue(qrToken)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid or inactive QR code."));
        Long restaurantId = table.getRestaurant().getId();
        CustomerSession session = customerSessionService.getActiveSession(
                httpRequest, restaurantId);
        if (session == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Customer authentication is required.");
        }

        customerAuthService.deleteAccount(session.getCustomerId(), restaurantId);
        customerSessionService.logout(httpRequest, restaurantId);

        HttpSession remainingSession = httpRequest.getSession(false);
        if (remainingSession != null) {
            remainingSession.invalidate();
        }
        expireSessionCookie(httpRequest, httpResponse);

        return ResponseEntity.ok(Map.of(
                "message", "Customer account deactivated. Profile anonymization is scheduled within 30 days."
        ));
    }

    private void expireSessionCookie(
            HttpServletRequest request,
            HttpServletResponse response) {
        var cookieConfig = request.getServletContext().getSessionCookieConfig();
        String cookieName = cookieConfig.getName();
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = "JSESSIONID";
        }
        String cookiePath = cookieConfig.getPath();
        if (cookiePath == null || cookiePath.isBlank()) {
            cookiePath = request.getContextPath().isBlank()
                    ? "/" : request.getContextPath() + "/";
        }
        ResponseCookie expiredCookie = ResponseCookie.from(cookieName, "")
                .path(cookiePath)
                .httpOnly(true)
                .secure(cookieConfig.isSecure())
                .sameSite(sessionCookieSameSite)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }
}
