UPDATE bills
SET bill_number = CONCAT('MIGRATION-', id);

UPDATE bills
SET bill_number = CONCAT('INV/', id);
