CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT NOT NULL,
    config_group VARCHAR(64) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    config_value VARCHAR(255),
    masked_value VARCHAR(255),
    sensitive_flag TINYINT(1) NOT NULL DEFAULT 0,
    editable_flag TINYINT(1) NOT NULL DEFAULT 1,
    description TEXT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by BIGINT,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_group_key (config_group, config_key),
    KEY idx_sys_config_group (config_group),
    KEY idx_sys_config_sensitive (sensitive_flag)
);

INSERT INTO sys_role (
    id, role_code, role_name, role_type, data_scope, permission_codes,
    status, sort_no, description, created_by, updated_by
) VALUES
    (100000000001, 'SUPER_ADMIN', '超级管理员', 'INTERNAL', 'ALL',
     '["system:config:read","system:config:write","system:audit:read","iam:role-application:approve","iam:role-grant","iam:user:read"]',
     'ACTIVE', 10, 'S3 最小安全 seed：全系统配置、审计和角色授权', 0, 0),
    (100000000002, 'OPS', '运营', 'INTERNAL', 'OWN_OR_TEAM',
     '["crm:lead:read","crm:lead:write","student:read","trade:order:read","report:business:read"]',
     'ACTIVE', 20, 'S3 最小安全 seed：运营线索和业务报表', 0, 0),
    (100000000003, 'SERVICE', '客服', 'INTERNAL', 'AFTER_SALE',
     '["student:read","trade:order:read","refund:review:write","invoice:read"]',
     'ACTIVE', 30, 'S3 最小安全 seed：售后退款和开票查询', 0, 0),
    (100000000004, 'WAREHOUSE', '仓管', 'INTERNAL', 'SUPPLY_CHAIN',
     '["inventory:sku:write","purchase:order:write","fulfillment:shipment:write","system:logistics-config:read"]',
     'ACTIVE', 40, 'S3 最小安全 seed：库存、采购和发货', 0, 0),
    (100000000005, 'ACCOUNTING', '代账人员', 'INTERNAL', 'FINANCE_AUTHORIZED',
     '["tax:invoice:write","finance:reconciliation:write","accounting:material:write","system:tax-config:read"]',
     'ACTIVE', 50, 'S3 最小安全 seed：财税、对账和代账材料', 0, 0);

INSERT INTO sys_user (
    id, user_no, user_type, display_name, mobile, wecom_user_id, status,
    created_by, updated_by
) VALUES
    (100000000001, 'DEMO_ADMIN', 'INTERNAL', 'S3 Admin', '13900000001', 's3_demo_admin', 'ACTIVE', 0, 0),
    (100000000002, 'DEMO_OPS', 'INTERNAL', 'S3 Ops', '13900000002', 's3_demo_ops', 'ACTIVE', 0, 0),
    (100000000003, 'DEMO_SERVICE', 'INTERNAL', 'S3 Service', '13900000003', 's3_demo_service', 'ACTIVE', 0, 0),
    (100000000004, 'DEMO_WAREHOUSE', 'INTERNAL', 'S3 Warehouse', '13900000004', 's3_demo_warehouse', 'ACTIVE', 0, 0),
    (100000000005, 'DEMO_ACCOUNTING', 'INTERNAL', 'S3 Accounting', '13900000005', 's3_demo_accounting', 'ACTIVE', 0, 0),
    (100000000006, 'DEMO_UNASSIGNED', 'INTERNAL', 'S3 Unassigned', '13900000006', 's3_demo_unassigned', 'ACTIVE', 0, 0);

INSERT INTO sys_user_role (
    id, user_id, role_id, grant_status, granted_by, granted_at, created_by, updated_by
) VALUES
    (100000000101, 100000000001, 100000000001, 'ACTIVE', 0, CURRENT_TIMESTAMP(3), 0, 0),
    (100000000102, 100000000002, 100000000002, 'ACTIVE', 0, CURRENT_TIMESTAMP(3), 0, 0),
    (100000000103, 100000000003, 100000000003, 'ACTIVE', 0, CURRENT_TIMESTAMP(3), 0, 0),
    (100000000104, 100000000004, 100000000004, 'ACTIVE', 0, CURRENT_TIMESTAMP(3), 0, 0),
    (100000000105, 100000000005, 100000000005, 'ACTIVE', 0, CURRENT_TIMESTAMP(3), 0, 0);

INSERT INTO sys_config (
    id, config_group, config_key, display_name, config_value, masked_value,
    sensitive_flag, editable_flag, description, created_by, updated_by
) VALUES
    (100000000201, 'PAYMENT', 'INTEGRATION_PAYMENT_MODE', '支付接入模式', 'mock', 'mock',
     0, 1, 'mock、sandbox、real 共用权限、配置和审计模型', 0, 0),
    (100000000202, 'PAYMENT', 'WECHAT_PAY_API_V3_KEY', '微信支付 API v3 Key', '<SENSITIVE_SET>', '********',
     1, 1, '敏感值只允许占位保存和脱敏展示', 0, 0),
    (100000000203, 'WECOM', 'WECOM_SECRET', '企业微信 Secret', '<SENSITIVE_SET>', '********',
     1, 1, '敏感值只允许占位保存和脱敏展示', 0, 0),
    (100000000204, 'MESSAGE', 'MAIL_PASSWORD', 'SMTP 密码', '<SENSITIVE_SET>', '********',
     1, 1, '敏感值只允许占位保存和脱敏展示', 0, 0),
    (100000000205, 'LOGISTICS', 'INTEGRATION_LOGISTICS_MODE', '物流接入模式', 'mock', 'mock',
     0, 1, '仓管可只读物流配置，写入仍需管理员权限', 0, 0),
    (100000000206, 'INVOICE', 'INTEGRATION_INVOICE_MODE', '开票接入模式', 'mock', 'mock',
     0, 1, '代账可只读开票配置，写入仍需管理员权限', 0, 0);
