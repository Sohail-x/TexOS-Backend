UPDATE kitchen_access_grants
SET revoked_at = CURRENT_TIMESTAMP
WHERE status = 'REVOKED'
  AND revoked_at IS NULL;
