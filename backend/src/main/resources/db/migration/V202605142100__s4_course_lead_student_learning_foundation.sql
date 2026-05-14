ALTER TABLE approval_record ADD COLUMN related_object_status_before VARCHAR(32);

CREATE TABLE IF NOT EXISTS lead_follow_record (
    id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    follow_method VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    next_follow_at DATETIME(3),
    follower_user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by BIGINT,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lead_follow_idempotency (idempotency_key),
    KEY idx_lead_follow_lead_time (lead_id, created_at),
    KEY idx_lead_follow_follower_time (follower_user_id, created_at)
);

INSERT INTO tax_rule (
    id, rule_no, rule_name, tax_category, tax_rate, invoice_item_name,
    status, description, created_by, updated_by
) VALUES
    (100000000401, 'TAX_S4_TRAINING', 'S4 培训服务税务规则', 'TRAINING_SERVICE', 0.0600,
     '培训服务', 'ACTIVE', 'S4 课程规格默认税务规则，供订单税务快照引用', 0, 0);

INSERT INTO inventory_sku (
    id, sku_no, sku_name, category_code, sku_type, unit, spec_attrs, spec_attrs_hash,
    default_supplier_id, cost_price_cent, current_stock, locked_stock, available_stock,
    safety_stock, status, image_url, created_by, updated_by
) VALUES
    (100000000402, 'SKU_S4_TEXTBOOK', 'S4 训练营教材包', 'COURSE_MATERIAL', 'MATERIAL',
     '套', '{"version":"S4"}', 'S4_TEXTBOOK', 100000000301, 2000, 100, 0, 100, 10,
     'ACTIVE', 'https://example.invalid/s4-textbook.png', 0, 0),
    (100000000403, 'SKU_S4_GIFT', 'S4 训练营赠品', 'COURSE_GIFT', 'GIFT',
     '份', '{"version":"S4"}', 'S4_GIFT', 100000000301, 1000, 100, 0, 100, 10,
     'ACTIVE', 'https://example.invalid/s4-gift.png', 0, 0);

INSERT INTO edu_student (
    id, student_no, user_id, mobile, nickname, real_name, wx_openid,
    wx_unionid, wecom_external_user_id, primary_lead_id, status, tags,
    created_by, updated_by
) VALUES
    (100000000413, 'STU_S4_MANUAL', null, '13900000011', 'S4 Manual Student',
     'S4 Manual Student', null, null, null, null, 'ACTIVE', '["S4_MANUAL"]', 0, 0);

INSERT INTO sys_user (
    id, user_no, user_type, display_name, mobile, wx_openid, wx_unionid,
    status, created_by, updated_by
) VALUES
    (100000000411, 'DEMO_DISABLED_STUDENT', 'STUDENT', 'S4 Disabled Student',
     '13900000012', 'wx_s4_disabled', 'union_s4_disabled', 'DISABLED', 0, 0);

INSERT INTO edu_student (
    id, student_no, user_id, mobile, nickname, real_name, wx_openid,
    wx_unionid, wecom_external_user_id, primary_lead_id, status, tags,
    created_by, updated_by
) VALUES
    (100000000412, 'STU_S4_DISABLED', 100000000411, '13900000012',
     'S4 Disabled Student', 'S4 Disabled Student', 'wx_s4_disabled',
     'union_s4_disabled', null, null, 'DISABLED', '["S4_DISABLED"]', 0, 0);
