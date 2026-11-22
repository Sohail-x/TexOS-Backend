ALTER TABLE restaurants
    ADD COLUMN reward_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reward_enabled_at DATETIME NULL,
    ADD COLUMN reward_required_order_days INT NULL,
    ADD COLUMN reward_spend_threshold DECIMAL(12,2) NULL,
    ADD COLUMN reward_cashback_amount DECIMAL(12,2) NULL;

ALTER TABLE customers
    ADD COLUMN cashback_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN reward_cycle_started_at DATETIME NULL;

ALTER TABLE restaurant_orders
    ADD COLUMN reward_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;

ALTER TABLE bills
    ADD COLUMN reward_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;

CREATE TABLE customer_account_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    identity_type VARCHAR(10) NOT NULL,
    identity_value VARCHAR(255) NOT NULL,
    customer_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_identity_value (identity_type, identity_value),
    UNIQUE KEY uk_customer_identity_account (identity_type, customer_id),
    CONSTRAINT fk_customer_identity_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

INSERT INTO customer_account_identities (identity_type, identity_value, customer_id)
SELECT 'MOBILE',
       CASE
           WHEN LENGTH(REGEXP_REPLACE(mobile_number, '[^0-9]', '')) = 12
                AND LEFT(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 2) = '91'
               THEN RIGHT(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 10)
           WHEN LENGTH(REGEXP_REPLACE(mobile_number, '[^0-9]', '')) = 11
                AND LEFT(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 1) = '0'
               THEN RIGHT(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 10)
           ELSE REGEXP_REPLACE(mobile_number, '[^0-9]', '')
       END,
       MIN(id)
FROM customers
GROUP BY CASE
             WHEN LENGTH(REGEXP_REPLACE(mobile_number, '[^0-9]', '')) = 12
                  AND LEFT(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 2) = '91'
                 THEN RIGHT(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 10)
             WHEN LENGTH(REGEXP_REPLACE(mobile_number, '[^0-9]', '')) = 11
                  AND LEFT(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 1) = '0'
                 THEN RIGHT(REGEXP_REPLACE(mobile_number, '[^0-9]', ''), 10)
             ELSE REGEXP_REPLACE(mobile_number, '[^0-9]', '')
         END;

INSERT INTO customer_account_identities (identity_type, identity_value, customer_id)
SELECT 'EMAIL', LOWER(TRIM(email)), MIN(id)
FROM customers
WHERE email IS NOT NULL AND TRIM(email) <> ''
GROUP BY LOWER(TRIM(email));
