package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.PasswordResetToken;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {
    private OwnerRepository ownerRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private JavaMailSender mailSender;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        ownerRepository = mock(OwnerRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        mailSender = mock(JavaMailSender.class);
        service = new PasswordResetService(ownerRepository, tokenRepository, passwordEncoder, mailSender);
        ReflectionTestUtils.setField(service, "mailUsername", "workspacexzen@gmail.com");
        ReflectionTestUtils.setField(service, "mailPassword", "abcdEFGH1234ijkl");
        ReflectionTestUtils.setField(service, "mailFrom", "workspacexzen@gmail.com");
        ReflectionTestUtils.setField(service, "frontendOrigin", "https://texos.example");
    }

    @Test
    void sendsOneTimeResetLinkAndStoresOnlyItsHash() throws Exception {
        Owner owner = owner();
        when(ownerRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));

        service.requestReset(" Owner@Example.com ");

        var tokenCaptor = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).deleteUnusedByOwnerId(7L);
        verify(tokenRepository).save(tokenCaptor.capture());
        var messageCaptor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("owner@example.com", message.getTo()[0]);
        assertEquals("workspacexzen@gmail.com", message.getFrom());
        assertTrue(message.getText().contains("https://texos.example/reset-password?token="));
        Matcher tokenMatcher = Pattern.compile("token=([^\\s]+)").matcher(message.getText());
        assertTrue(tokenMatcher.find());
        String rawToken = URLDecoder.decode(tokenMatcher.group(1), StandardCharsets.UTF_8);
        String expectedHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, savedToken.getTokenHash());
        assertTrue(savedToken.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
        assertTrue(savedToken.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(31)));
    }

    @Test
    void doesNotRevealWhetherEmailHasAnAccount() {
        when(ownerRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        service.requestReset("missing@example.com");

        verifyNoInteractions(mailSender, tokenRepository);
    }

    @Test
    void reportsMissingMailCredentialsInsteadOfClaimingTheResetEmailWasSent() {
        when(ownerRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner()));
        ReflectionTestUtils.setField(service, "mailPassword", "");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.requestReset("owner@example.com"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        verifyNoInteractions(mailSender, tokenRepository);
    }

    @Test
    void rejectsNonAppPasswordBeforeCreatingATokenOrSendingMail() {
        when(ownerRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner()));
        ReflectionTestUtils.setField(service, "mailPassword", "regular-account-password");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.requestReset("owner@example.com"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertTrue(exception.getReason().contains("16-character Gmail App Password"));
        verifyNoInteractions(mailSender, tokenRepository);
    }

    @Test
    void resetChangesPasswordAndConsumesToken() {
        String rawToken = "one-time-reset-token";
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setOwnerId(7L);
        resetToken.setTokenHash(hash(rawToken));
        resetToken.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        Owner owner = owner();
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(eq(hash(rawToken)), any()))
                .thenReturn(Optional.of(resetToken));
        when(ownerRepository.findById(7L)).thenReturn(Optional.of(owner));
        when(passwordEncoder.encode("new-password-123")).thenReturn("bcrypt-hash");

        service.resetPassword(rawToken, "new-password-123");

        assertEquals("bcrypt-hash", owner.getPasswordHash());
        assertEquals(1, owner.getTokenVersion());
        assertNotNull(resetToken.getUsedAt());
        verify(ownerRepository).save(owner);
        verify(tokenRepository).save(resetToken);
    }

    @Test
    void rejectsUnknownOrExpiredResetTokens() {
        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(eq(hash("expired-token")), any()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword("expired-token", "new-password-123"));
        verifyNoInteractions(ownerRepository, passwordEncoder);
    }

    private Owner owner() {
        Owner owner = new Owner();
        owner.setId(7L);
        owner.setFullName("TexOS Owner");
        owner.setEmail("owner@example.com");
        owner.setActive(true);
        return owner;
    }

    private String hash(String rawToken) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
