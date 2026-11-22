ALTER TABLE owners
    MODIFY COLUMN email VARCHAR(150) NULL,
    ADD COLUMN google_sub VARCHAR(255) NULL,
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD UNIQUE KEY uk_owners_google_sub (google_sub);

CREATE TABLE subscription_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    price DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    duration_days INT NOT NULL,
    trial_plan BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_plans_name (name)
);

CREATE TABLE subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status ENUM('PENDING','TRIALING','ACTIVE','EXPIRED','CANCELLED') NOT NULL,
    start_date DATETIME NULL,
    end_date DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_subscriptions_restaurant (restaurant_id, created_at),
    KEY idx_subscriptions_expiry (status, end_date),
    CONSTRAINT fk_subscriptions_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
);

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    razorpay_order_id VARCHAR(100) NOT NULL,
    razorpay_payment_id VARCHAR(100) NULL,
    razorpay_signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    payment_status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_order (razorpay_order_id),
    UNIQUE KEY uk_payments_payment (razorpay_payment_id),
    KEY idx_payments_restaurant (restaurant_id, created_at),
    CONSTRAINT fk_payments_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    CONSTRAINT fk_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id),
    CONSTRAINT fk_payments_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
);

CREATE TABLE webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    razorpay_event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processing_status VARCHAR(20) NOT NULL,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_event_id (razorpay_event_id)
);

CREATE TABLE trial_claims (
    restaurant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (restaurant_id),
    UNIQUE KEY uk_trial_claim_subscription (subscription_id),
    CONSTRAINT fk_trial_claim_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    CONSTRAINT fk_trial_claim_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
);

INSERT INTO subscription_plans (name, description, price, currency, duration_days, trial_plan, active)
VALUES
    ('Free Trial', '30-day free trial', 0.00, 'INR', 30, TRUE, TRUE),
    ('Starter', 'Everything you need to get started.', 499.00, 'INR', 30, FALSE, TRUE),
    ('Growth', 'More control over daily operations.', 799.00, 'INR', 30, FALSE, TRUE),
    ('Premium', 'Advanced tools to grow your business.', 1299.00, 'INR', 30, FALSE, TRUE);
