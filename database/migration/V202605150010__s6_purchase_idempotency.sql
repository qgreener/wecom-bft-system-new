ALTER TABLE purchase_order ADD COLUMN idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX uk_purchase_idempotency_key ON purchase_order (idempotency_key);
