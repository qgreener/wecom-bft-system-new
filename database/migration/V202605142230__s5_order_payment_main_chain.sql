ALTER TABLE trade_order
    ADD COLUMN client_request_no VARCHAR(64);

ALTER TABLE trade_order
    ADD COLUMN idempotency_key VARCHAR(128);

ALTER TABLE trade_order
    ADD COLUMN confirm_token VARCHAR(128);

CREATE UNIQUE INDEX uk_trade_order_student_idempotency
    ON trade_order (student_id, idempotency_key);

CREATE INDEX idx_trade_order_student_request
    ON trade_order (student_id, client_request_no);
