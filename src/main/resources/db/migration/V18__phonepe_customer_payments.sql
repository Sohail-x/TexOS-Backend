ALTER TABLE payments
    CHANGE COLUMN razorpay_order_id provider_order_id VARCHAR(100) NOT NULL,
    CHANGE COLUMN razorpay_payment_id provider_payment_id VARCHAR(100) NULL,
    CHANGE COLUMN razorpay_signature_verified provider_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN merchant_order_id VARCHAR(63) NULL AFTER provider_order_id,
    ADD COLUMN checkout_url TEXT NULL AFTER merchant_order_id,
    ADD UNIQUE KEY uk_subscription_payment_merchant_order (merchant_order_id);

ALTER TABLE webhook_events
    CHANGE COLUMN razorpay_event_id provider_event_id VARCHAR(100) NOT NULL;

CREATE TABLE restaurant_phonepe_configs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    client_id VARCHAR(150) NOT NULL,
    client_version INT NOT NULL,
    client_secret_ciphertext VARCHAR(512) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    webhook_key_id VARCHAR(150) NOT NULL,
    webhook_secret_ciphertext VARCHAR(512) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_restaurant_phonepe_config_restaurant (restaurant_id),
    CONSTRAINT fk_restaurant_phonepe_config_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);

CREATE TABLE restaurant_payment_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    customer_id BIGINT NULL,
    order_session_key VARCHAR(36) NULL,
    bill_key VARCHAR(100) NOT NULL,
    active_key VARCHAR(100) NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    payment_status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(30) NULL,
    payment_reference VARCHAR(150) NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_restaurant_payment_active_key (restaurant_id, active_key),
    KEY idx_restaurant_payment_history (restaurant_id, created_at),
    KEY idx_restaurant_payment_customer (customer_id, created_at),
    CONSTRAINT fk_restaurant_payment_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    CONSTRAINT fk_restaurant_payment_table
        FOREIGN KEY (table_id) REFERENCES restaurant_tables(id),
    CONSTRAINT fk_restaurant_payment_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE payment_transaction_orders (
    payment_transaction_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    PRIMARY KEY (payment_transaction_id, order_id),
    UNIQUE KEY uk_payment_transaction_order_order (order_id),
    CONSTRAINT fk_payment_transaction_orders_transaction
        FOREIGN KEY (payment_transaction_id) REFERENCES restaurant_payment_transactions(id),
    CONSTRAINT fk_payment_transaction_orders_order
        FOREIGN KEY (order_id) REFERENCES restaurant_orders(id)
);

CREATE TABLE payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_transaction_id BIGINT NOT NULL,
    merchant_order_id VARCHAR(63) NOT NULL,
    provider_order_id VARCHAR(100) NULL,
    provider_payment_reference VARCHAR(150) NULL,
    checkout_url TEXT NULL,
    payment_status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(40) NULL,
    provider_state VARCHAR(30) NULL,
    error_code VARCHAR(100) NULL,
    amount_minor BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_attempt_merchant_order (merchant_order_id),
    UNIQUE KEY uk_payment_attempt_provider_order (provider_order_id),
    KEY idx_payment_attempt_status (payment_status, updated_at),
    KEY idx_payment_attempt_transaction (payment_transaction_id, created_at),
    CONSTRAINT fk_payment_attempt_transaction
        FOREIGN KEY (payment_transaction_id) REFERENCES restaurant_payment_transactions(id)
);

CREATE TABLE payment_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_transaction_id BIGINT NOT NULL,
    payment_attempt_id BIGINT NULL,
    event_type VARCHAR(50) NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_id BIGINT NULL,
    details_json LONGTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_payment_audit_transaction (payment_transaction_id, created_at),
    CONSTRAINT fk_payment_audit_transaction
        FOREIGN KEY (payment_transaction_id) REFERENCES restaurant_payment_transactions(id),
    CONSTRAINT fk_payment_audit_attempt
        FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts(id)
);

CREATE TABLE offline_assistance_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    customer_id BIGINT NULL,
    order_session_key VARCHAR(36) NULL,
    latest_order_id BIGINT NOT NULL,
    bill_key VARCHAR(100) NOT NULL,
    active_bill_key VARCHAR(100) NULL,
    request_status VARCHAR(20) NOT NULL,
    customer_name VARCHAR(100) NULL,
    customer_mobile VARCHAR(20) NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_at DATETIME NULL,
    assigned_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_offline_assistance_active_bill (restaurant_id, active_bill_key),
    KEY idx_offline_assistance_pending (restaurant_id, request_status, requested_at),
    CONSTRAINT fk_offline_assistance_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    CONSTRAINT fk_offline_assistance_table
        FOREIGN KEY (table_id) REFERENCES restaurant_tables(id),
    CONSTRAINT fk_offline_assistance_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_offline_assistance_order
        FOREIGN KEY (latest_order_id) REFERENCES restaurant_orders(id),
    CONSTRAINT fk_offline_assistance_owner
        FOREIGN KEY (assigned_by) REFERENCES owners(id)
);

CREATE TABLE restaurant_payment_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    payment_transaction_id BIGINT NULL,
    assistance_request_id BIGINT NULL,
    priority INT NOT NULL,
    notification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_notification_transaction (payment_transaction_id, notification_type),
    UNIQUE KEY uk_payment_notification_assistance (assistance_request_id, notification_type),
    KEY idx_payment_notification_queue (restaurant_id, notification_status, priority, created_at),
    CONSTRAINT fk_payment_notification_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    CONSTRAINT fk_payment_notification_transaction
        FOREIGN KEY (payment_transaction_id) REFERENCES restaurant_payment_transactions(id),
    CONSTRAINT fk_payment_notification_assistance
        FOREIGN KEY (assistance_request_id) REFERENCES offline_assistance_requests(id)
);

CREATE TABLE phonepe_webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_hash CHAR(64) NOT NULL,
    merchant_order_id VARCHAR(63) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    signature_verified BOOLEAN NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_phonepe_webhook_event_hash (event_hash),
    KEY idx_phonepe_webhook_pending (processing_status, received_at)
);
