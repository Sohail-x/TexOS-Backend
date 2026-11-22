CREATE TABLE loyalty_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    actor_identity VARCHAR(255) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NULL,
    details VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_loyalty_audit_restaurant_created (restaurant_id, created_at),
    CONSTRAINT fk_loyalty_audit_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);
