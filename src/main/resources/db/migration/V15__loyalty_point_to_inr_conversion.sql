ALTER TABLE loyalty_programs
    ADD COLUMN conversion_points INT NOT NULL DEFAULT 100,
    ADD COLUMN conversion_inr DECIMAL(12,2) NOT NULL DEFAULT 10.00;

ALTER TABLE loyalty_redemptions
    MODIFY reward_id BIGINT NULL;
