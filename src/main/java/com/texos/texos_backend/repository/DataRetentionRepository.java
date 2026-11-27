package com.texos.texos_backend.repository;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DataRetentionRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataRetentionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int deleteOldInactiveKitchenGrants(LocalDateTime cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM kitchen_access_grants
                WHERE (status = 'EXPIRED' AND expires_at <= ?)
                   OR (status = 'REVOKED' AND revoked_at <= ?)
                   OR (status = 'USED' AND used_at <= ?)
                   OR (status = 'ACTIVE' AND expires_at <= ?)
                LIMIT 500
                """, cutoff, cutoff, cutoff, cutoff);
    }

    public int deleteOldInactiveOrderApprovalCodes(LocalDateTime cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM order_approval_access_codes
                WHERE (status = 'EXPIRED' AND expires_at <= ?)
                   OR (status = 'REVOKED' AND revoked_at <= ?)
                   OR (status = 'USED' AND used_at <= ?)
                   OR (status = 'ACTIVE' AND expires_at <= ?)
                LIMIT 500
                """, cutoff, cutoff, cutoff, cutoff);
    }

    public int deleteOldProcessedWebhookEvents(LocalDateTime cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM webhook_events
                WHERE processing_status = 'PROCESSED'
                  AND processed_at <= ?
                LIMIT 500
                """, cutoff);
    }

    public int deleteOldProcessedPhonePeWebhookEvents(LocalDateTime cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM phonepe_webhook_events
                WHERE processing_status = 'PROCESSED'
                  AND processed_at <= ?
                LIMIT 500
                """, cutoff);
    }

    public int deleteOldClosedPaymentNotifications(LocalDateTime cutoff) {
        return jdbcTemplate.update("""
                DELETE FROM restaurant_payment_notifications
                WHERE notification_status <> 'PENDING'
                  AND closed_at <= ?
                LIMIT 500
                """, cutoff);
    }

    public int purgeDeletedRestaurantRegistrationMetadata(LocalDateTime cutoff) {
        int pushSubscriptions = jdbcTemplate.update("""
                DELETE FROM order_approval_push_subscriptions
                WHERE session_id IN (
                    SELECT session_id
                    FROM order_approval_sessions
                    WHERE restaurant_id IN (
                        SELECT id FROM restaurants
                        WHERE deleted_at <= ?
                           OR owner_id IN (
                               SELECT id FROM owners WHERE deleted_at <= ?
                           )
                    )
                )
                LIMIT 500
                """, cutoff, cutoff);
        int accessCodes = jdbcTemplate.update("""
                DELETE FROM order_approval_access_codes
                WHERE restaurant_id IN (
                    SELECT id FROM restaurants
                    WHERE deleted_at <= ?
                       OR owner_id IN (
                           SELECT id FROM owners WHERE deleted_at <= ?
                       )
                )
                LIMIT 500
                """, cutoff, cutoff);
        int kitchenGrants = jdbcTemplate.update("""
                DELETE FROM kitchen_access_grants
                WHERE restaurant_id IN (
                    SELECT id FROM restaurants
                    WHERE deleted_at <= ?
                       OR owner_id IN (
                           SELECT id FROM owners WHERE deleted_at <= ?
                       )
                )
                LIMIT 500
                """, cutoff, cutoff);
        int trialClaims = jdbcTemplate.update("""
                DELETE FROM trial_claims
                WHERE restaurant_id IN (
                    SELECT id FROM restaurants
                    WHERE deleted_at <= ?
                       OR owner_id IN (
                           SELECT id FROM owners WHERE deleted_at <= ?
                       )
                )
                LIMIT 500
                """, cutoff, cutoff);
        int approvalSessions = jdbcTemplate.update("""
                DELETE FROM order_approval_sessions
                WHERE restaurant_id IN (
                    SELECT id FROM restaurants
                    WHERE deleted_at <= ?
                       OR owner_id IN (
                           SELECT id FROM owners WHERE deleted_at <= ?
                       )
                )
                LIMIT 500
                """, cutoff, cutoff);
        int resetTokens = jdbcTemplate.update("""
                DELETE FROM password_reset_tokens
                WHERE owner_id IN (
                    SELECT id FROM owners WHERE deleted_at <= ?
                )
                LIMIT 500
                """, cutoff);

        return pushSubscriptions + accessCodes + kitchenGrants + trialClaims
                + approvalSessions + resetTokens;
    }
}
