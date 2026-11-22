ALTER TABLE restaurants
    ADD COLUMN order_approval_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN order_approval_token_hash VARCHAR(64) NULL,
    ADD COLUMN order_approval_token_encrypted TEXT NULL;

CREATE UNIQUE INDEX uk_restaurant_order_approval_token_hash
    ON restaurants (order_approval_token_hash);

ALTER TABLE restaurant_orders
    ADD COLUMN approval_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN approval_updated_at DATETIME(6) NULL,
    ADD COLUMN approval_session_id BINARY(16) NULL,
    ADD INDEX idx_order_approval_restaurant_status (restaurant_id, approval_status, created_at);

CREATE TABLE order_approval_sessions (
    session_id BINARY(16) NOT NULL,
    restaurant_id BIGINT NOT NULL,
    access_token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id),
    INDEX idx_order_approval_session_restaurant (restaurant_id),
    CONSTRAINT fk_order_approval_session_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id)
) ENGINE=InnoDB;

CREATE TABLE order_approval_push_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BINARY(16) NOT NULL,
    endpoint_hash VARCHAR(64) NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh VARCHAR(256) NOT NULL,
    auth_key VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_approval_push_endpoint_hash UNIQUE (endpoint_hash),
    CONSTRAINT fk_order_approval_push_session
        FOREIGN KEY (session_id) REFERENCES order_approval_sessions (session_id)
        ON DELETE CASCADE,
    INDEX idx_order_approval_push_session (session_id)
) ENGINE=InnoDB;
