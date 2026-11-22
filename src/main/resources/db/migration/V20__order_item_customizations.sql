ALTER TABLE order_items
    ADD COLUMN customization VARCHAR(500) NULL AFTER item_name;
