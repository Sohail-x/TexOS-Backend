ALTER TABLE restaurant_payment_notifications
    DROP INDEX uk_payment_notification_assistance,
    ADD INDEX idx_payment_notification_assistance (assistance_request_id, notification_type);
