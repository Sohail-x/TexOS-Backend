ALTER TABLE order_approval_access_codes
    ADD COLUMN revoked_at DATETIME(6) NULL;

UPDATE order_approval_access_codes
SET revoked_at = CURRENT_TIMESTAMP
WHERE status = 'REVOKED' AND revoked_at IS NULL;

CREATE INDEX idx_kitchen_access_retention
    ON kitchen_access_grants (status, expires_at, revoked_at, used_at);

CREATE INDEX idx_order_approval_access_retention
    ON order_approval_access_codes (status, expires_at, revoked_at, used_at);

CREATE INDEX idx_webhook_events_processed_retention
    ON webhook_events (processing_status, processed_at);

CREATE INDEX idx_phonepe_webhook_events_processed_retention
    ON phonepe_webhook_events (processing_status, processed_at);

CREATE INDEX idx_payment_notifications_closed_retention
    ON restaurant_payment_notifications (notification_status, closed_at);
