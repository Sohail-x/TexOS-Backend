ALTER TABLE restaurants
    DROP COLUMN reward_enabled,
    DROP COLUMN reward_enabled_at,
    DROP COLUMN reward_required_order_days,
    DROP COLUMN reward_spend_threshold,
    DROP COLUMN reward_cashback_amount;

ALTER TABLE customers
    DROP COLUMN cashback_balance,
    DROP COLUMN reward_cycle_started_at;
