UPDATE restaurant_orders
SET order_number = CONCAT('TX - #', LPAD(id, 7, '0'))
WHERE order_number IS NULL
   OR order_number = ''
   OR order_number NOT REGEXP '^TX - #[0-9]+$';
