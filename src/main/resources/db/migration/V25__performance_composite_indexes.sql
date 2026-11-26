CREATE INDEX idx_order_rest_created_approval
    ON restaurant_orders (restaurant_id, created_at, approval_status);

CREATE INDEX idx_order_rest_status_approval_created
    ON restaurant_orders (restaurant_id, order_status, approval_status, created_at);

CREATE INDEX idx_loyalty_redemption_rest_created
    ON loyalty_redemptions (restaurant_id, created_at);
