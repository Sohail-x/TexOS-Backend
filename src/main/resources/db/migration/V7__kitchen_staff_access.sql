CREATE TABLE IF NOT EXISTS kitchen_access_grants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status ENUM('ACTIVE', 'EXPIRED', 'REVOKED', 'USED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    session_id BINARY(16) NULL,
    session_expires_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_kitchen_grant_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_kitchen_grant_session_id UNIQUE (session_id),
    CONSTRAINT fk_kitchen_grant_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id),
    INDEX idx_kitchen_grant_restaurant_created (restaurant_id, created_at)
) ENGINE=InnoDB;
