UPDATE owners
SET deleted_at = COALESCE(updated_at, created_at),
    anonymized_at = COALESCE(updated_at, created_at)
WHERE auth_provider = 'DELETED'
  AND deleted_at IS NULL
  AND anonymized_at IS NULL;

UPDATE restaurants restaurant
JOIN owners owner ON owner.id = restaurant.owner_id
SET restaurant.deleted_at = COALESCE(
        restaurant.updated_at,
        restaurant.created_at,
        owner.deleted_at
    )
WHERE owner.auth_provider = 'DELETED'
  AND restaurant.deleted_at IS NULL;

UPDATE customers
SET deleted_at = COALESCE(updated_at, created_at),
    anonymized_at = COALESCE(updated_at, created_at)
WHERE active = FALSE
  AND customer_name LIKE 'Deleted customer %'
  AND deleted_at IS NULL
  AND anonymized_at IS NULL;
