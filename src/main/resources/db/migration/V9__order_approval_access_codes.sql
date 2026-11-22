ALTER TABLE restaurants
    DROP INDEX uk_restaurant_order_approval_token_hash,
    DROP COLUMN order_approval_token_hash,
    DROP COLUMN order_approval_token_encrypted;

ALTER TABLE order_approval_sessions
    CHANGE COLUMN access_token_hash access_code_hash VARCHAR(64) NOT NULL;

CREATE TABLE order_approval_access_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    session_id BINARY(16) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_approval_access_code_hash UNIQUE (code_hash),
    CONSTRAINT uk_order_approval_access_code_session UNIQUE (session_id),
    CONSTRAINT fk_order_approval_access_code_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    CONSTRAINT fk_order_approval_access_code_session
        FOREIGN KEY (session_id) REFERENCES order_approval_sessions (session_id),
    INDEX idx_order_approval_access_code_restaurant_created (restaurant_id, created_at)
) ENGINE=InnoDB;
