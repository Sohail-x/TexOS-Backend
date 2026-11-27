package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.PasswordResetToken;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetService.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LIFETIME_MINUTES = 30;

    private final OwnerRepository ownerRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final BrevoEmailService emailService;

    @Value("${app.frontend-origin}")
    private String frontendOrigin;

    @Transactional
    public void requestReset(String email) {

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        ownerRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .filter(Owner::isActive)
                .ifPresent(this::createAndSendResetLink);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {

        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = tokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                        tokenHash, utcNow())
                .orElseThrow(() -> new IllegalArgumentException(
                        "This password reset link is invalid or has expired. Request a new link."));

        Owner owner = ownerRepository.findById(resetToken.getOwnerId())
                .filter(Owner::isActive)
                .filter(foundOwner -> foundOwner.getDeletedAt() == null)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Owner account not found"));

        owner.setPasswordHash(passwordEncoder.encode(newPassword));
        owner.setTokenVersion(owner.getTokenVersion() + 1);
        ownerRepository.save(owner);

        resetToken.setUsedAt(utcNow());
        tokenRepository.save(resetToken);
    }

    private void createAndSendResetLink(Owner owner) {

        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Password reset email is not configured.");
        }

        byte[] randomToken = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomToken);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomToken);

        tokenRepository.deleteUnusedByOwnerId(owner.getId());

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setOwnerId(owner.getId());
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setExpiresAt(
                utcNow().plusMinutes(TOKEN_LIFETIME_MINUTES));

        tokenRepository.save(resetToken);

        String resetUrl = frontendOrigin.replaceAll("/+$", "")
                + "/reset-password?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        String emailText = "Hello " + owner.getFullName() + ",\n\n"
                + "Use the following link to reset your TexOS password. "
                + "It expires in 30 minutes and can only be used once:\n\n"
                + resetUrl + "\n\n"
                + "If you did not request this, you can ignore this email.";

        try {
            emailService.send(
                    owner.getEmail(),
                    "Reset your TexOS password",
                    emailText
            );

        } catch (IllegalStateException ex) {
            log.error("Password reset email delivery failed: {}",
                    ex.getMessage());

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Password reset email could not be sent. Please try again later.",
                    ex
            );
        }
    }

    private String hashToken(String rawToken) {

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", ex);
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
