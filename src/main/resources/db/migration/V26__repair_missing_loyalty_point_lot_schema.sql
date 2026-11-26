CREATE TABLE IF NOT EXISTS loyalty_point_lots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    earned_points BIGINT NOT NULL,
    available_points BIGINT NOT NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_loyalty_lot_account_expiry (account_id, expires_at),
    KEY idx_loyalty_lot_order (order_id),
    CONSTRAINT fk_loyalty_lot_account FOREIGN KEY (account_id) REFERENCES loyalty_accounts(id),
    CONSTRAINT fk_loyalty_lot_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    CONSTRAINT fk_loyalty_lot_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_loyalty_lot_order FOREIGN KEY (order_id) REFERENCES restaurant_orders(id)
);

CREATE TABLE IF NOT EXISTS loyalty_point_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    redemption_id BIGINT NOT NULL,
    point_lot_id BIGINT NOT NULL,
    points BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_loyalty_allocation_redemption (redemption_id, status),
    CONSTRAINT fk_loyalty_allocation_redemption FOREIGN KEY (redemption_id) REFERENCES loyalty_redemptions(id),
    CONSTRAINT fk_loyalty_allocation_lot FOREIGN KEY (point_lot_id) REFERENCES loyalty_point_lots(id)
);

INSERT INTO loyalty_point_lots (
    account_id, restaurant_id, customer_id, order_id,
    earned_points, available_points, expires_at, created_at
)
SELECT la.id, la.restaurant_id, la.customer_id, NULL,
       la.available_points, la.available_points, NULL, NOW()
FROM loyalty_accounts la
WHERE la.available_points > 0
  AND NOT EXISTS (
      SELECT 1
      FROM loyalty_point_lots lot
      WHERE lot.account_id = la.id
  );
