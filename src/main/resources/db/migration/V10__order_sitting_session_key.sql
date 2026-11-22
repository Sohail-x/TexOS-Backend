ALTER TABLE restaurant_orders
    ADD COLUMN order_session_key VARCHAR(36) NULL;

CREATE INDEX idx_order_sitting_session
    ON restaurant_orders (restaurant_id, table_id, order_session_key, created_at);
