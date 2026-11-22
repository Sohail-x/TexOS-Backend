CREATE TABLE payment_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_transaction_id BIGINT NOT NULL,
    receipt_number VARCHAR(40) NOT NULL,
    receipt_json LONGTEXT NOT NULL,
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_receipt_transaction (payment_transaction_id),
    UNIQUE KEY uk_payment_receipt_number (receipt_number),
    CONSTRAINT fk_payment_receipt_transaction
        FOREIGN KEY (payment_transaction_id) REFERENCES restaurant_payment_transactions(id)
);
