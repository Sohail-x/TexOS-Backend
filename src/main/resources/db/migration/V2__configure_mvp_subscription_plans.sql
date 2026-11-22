-- Preserve existing subscription and payment history, while exposing only the
-- two plans supported by the MVP. Older plans remain in the database so their
-- historical references are intact, but are no longer selectable.
UPDATE subscription_plans
SET active = FALSE
WHERE active = TRUE;

INSERT INTO subscription_plans
    (name, description, price, currency, duration_days, trial_plan, active, created_at, updated_at)
VALUES
    ('1 Month Free Trial', 'Full TexOS workspace access for 30 days. Available once per restaurant.', 0.00, 'INR', 30, TRUE, TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    ('TexOS Paid Subscription', 'Full TexOS workspace access for 30 days.', 799.00, 'INR', 30, FALSE, TRUE, UTC_TIMESTAMP(), UTC_TIMESTAMP())
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    price = VALUES(price),
    currency = VALUES(currency),
    duration_days = VALUES(duration_days),
    trial_plan = VALUES(trial_plan),
    active = VALUES(active);
