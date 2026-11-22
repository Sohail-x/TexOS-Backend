CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_token_hash (token_hash),
    KEY idx_password_reset_owner (owner_id),
    KEY idx_password_reset_expiry (expires_at)
);

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT fk_password_reset_owner FOREIGN KEY (owner_id) REFERENCES owners(id);
