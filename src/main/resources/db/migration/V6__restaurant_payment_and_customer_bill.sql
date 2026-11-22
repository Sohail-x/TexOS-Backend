ALTER TABLE restaurants
    ADD COLUMN payment_qr_image LONGTEXT NULL,
    ADD COLUMN upi_id VARCHAR(255) NULL;
