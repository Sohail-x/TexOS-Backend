ALTER TABLE owners
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN anonymized_at TIMESTAMP NULL,
    MODIFY COLUMN password_hash VARCHAR(255) NULL;

CREATE INDEX idx_owners_retention_deleted_anonymized
    ON owners (deleted_at, anonymized_at);

ALTER TABLE restaurants
    ADD COLUMN deleted_at TIMESTAMP NULL;

CREATE INDEX idx_restaurants_deleted_at
    ON restaurants (deleted_at);

ALTER TABLE customers
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN anonymized_at TIMESTAMP NULL,
    ADD COLUMN last_activity_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN mobile_number VARCHAR(16) NULL,
    MODIFY COLUMN password_hash VARCHAR(100) NULL;

UPDATE customers
SET last_activity_at = CURRENT_TIMESTAMP
WHERE last_activity_at IS NULL;

CREATE INDEX idx_customers_retention_deleted_anonymized
    ON customers (deleted_at, anonymized_at);

CREATE INDEX idx_customers_last_activity
    ON customers (last_activity_at);

CREATE INDEX idx_bills_created_at_retention
    ON bills (created_at);

CREATE INDEX idx_payments_created_at_retention
    ON payments (created_at);
