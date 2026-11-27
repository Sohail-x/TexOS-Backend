ALTER TABLE bills
    ADD COLUMN order_session_key VARCHAR(36) NULL,
    ADD COLUMN superseded_by_bill_id BIGINT NULL;

UPDATE bills bill
JOIN restaurant_orders restaurant_order ON restaurant_order.id = bill.order_id
SET bill.order_session_key = restaurant_order.order_session_key
WHERE restaurant_order.order_session_key IS NOT NULL;

UPDATE bills canonical
JOIN (
    SELECT MIN(id) AS canonical_bill_id,
           order_session_key,
           SUM(subtotal) AS subtotal,
           SUM(tax_amount) AS tax_amount,
           SUM(service_charge_amount) AS service_charge_amount,
           SUM(reward_discount_amount) AS reward_discount_amount,
           SUM(total_amount) AS total_amount
    FROM bills
    WHERE order_session_key IS NOT NULL
    GROUP BY order_session_key
    HAVING COUNT(*) > 1
) sitting ON sitting.canonical_bill_id = canonical.id
SET canonical.subtotal = sitting.subtotal,
    canonical.tax_amount = sitting.tax_amount,
    canonical.service_charge_amount = sitting.service_charge_amount,
    canonical.reward_discount_amount = sitting.reward_discount_amount,
    canonical.total_amount = sitting.total_amount,
    canonical.bill_version = canonical.bill_version + 1;

UPDATE bills duplicate
JOIN (
    SELECT order_session_key, MIN(id) AS canonical_bill_id
    FROM bills
    WHERE order_session_key IS NOT NULL
    GROUP BY order_session_key
) sitting ON sitting.order_session_key = duplicate.order_session_key
SET duplicate.superseded_by_bill_id = sitting.canonical_bill_id,
    duplicate.order_session_key = NULL
WHERE duplicate.id <> sitting.canonical_bill_id;

CREATE UNIQUE INDEX uk_bills_order_session_key
    ON bills (order_session_key);
