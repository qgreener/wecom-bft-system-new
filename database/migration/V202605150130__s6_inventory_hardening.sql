ALTER TABLE inventory_sku ADD COLUMN idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX uk_inventory_sku_idempotency_key ON inventory_sku (idempotency_key);

UPDATE sys_config
SET config_value = '100000',
    masked_value = '100000',
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = 0,
    version = version + 1
WHERE config_group = 'PURCHASE'
  AND config_key = 'PURCHASE_APPROVAL_THRESHOLD_CENT'
  AND config_value = '500000';
