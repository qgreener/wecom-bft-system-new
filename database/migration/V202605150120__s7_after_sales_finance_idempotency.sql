ALTER TABLE student_invoice_title ADD COLUMN idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX uk_invoice_title_student_idempotency ON student_invoice_title (student_id, idempotency_key);

ALTER TABLE tax_invoice ADD COLUMN idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX uk_tax_invoice_student_idempotency ON tax_invoice (student_id, idempotency_key);

ALTER TABLE finance_reconciliation_batch ADD COLUMN idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX uk_recon_batch_idempotency_key ON finance_reconciliation_batch (idempotency_key);

ALTER TABLE acct_material ADD COLUMN idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX uk_acct_material_idempotency_key ON acct_material (idempotency_key);
