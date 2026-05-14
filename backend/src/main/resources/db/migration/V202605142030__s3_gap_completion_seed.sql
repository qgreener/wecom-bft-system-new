INSERT INTO sys_role (
    id, role_code, role_name, role_type, data_scope, permission_codes,
    status, sort_no, description, created_by, updated_by
) VALUES
    (100000000006, 'EDU_ADMIN', '教务', 'INTERNAL', 'COURSE_ALL',
     '["course:spec:write","course:lesson:read","student:read","report:learning:read"]',
     'ACTIVE', 25, 'S3 补漏 seed：课程规格、课节和学员学习数据教务范围', 0, 0),
    (100000000007, 'TEACHER', '讲师', 'INTERNAL', 'OWN_COURSE',
     '["course:lesson:write","course:lesson:read","learning:record:read"]',
     'ACTIVE', 26, 'S3 补漏 seed：本人课程课节和学习记录范围', 0, 0);

INSERT INTO supplier (
    id, supplier_no, supplier_name, short_name, contact_name, contact_mobile,
    contact_email, tax_no, address, settlement_method, access_status, status,
    created_by, updated_by
) VALUES
    (100000000301, 'SUP_S3_DEMO', 'S3 演示供货商', 'S3供货商', 'S3 Supplier Contact',
     '13900000010', 'supplier-s3-demo@example.invalid', 'TAX-S3-DEMO-MASKED',
     'S3 demo masked address', 'MOCK_SETTLEMENT', 'ENABLED', 'ACTIVE', 0, 0);

INSERT INTO sys_user (
    id, user_no, user_type, display_name, mobile, wecom_user_id, wx_openid,
    wx_unionid, supplier_id, status, created_by, updated_by
) VALUES
    (100000000007, 'DEMO_EDU_ADMIN', 'INTERNAL', 'S3 Edu Admin', '13900000007',
     's3_demo_edu_admin', null, null, null, 'ACTIVE', 0, 0),
    (100000000008, 'DEMO_TEACHER', 'INTERNAL', 'S3 Teacher', '13900000008',
     's3_demo_teacher', null, null, null, 'ACTIVE', 0, 0),
    (100000000009, 'DEMO_APP_STUDENT', 'STUDENT', 'S3 App Student', '13900000009',
     null, 'wx_s3_demo_student', 'union_s3_demo_student', null, 'ACTIVE', 0, 0),
    (100000000010, 'DEMO_SUPPLIER', 'SUPPLIER', 'S3 Supplier User', '13900000010',
     null, null, null, 100000000301, 'ACTIVE', 0, 0);

INSERT INTO sys_user_role (
    id, user_id, role_id, grant_status, granted_by, granted_at, created_by, updated_by
) VALUES
    (100000000106, 100000000007, 100000000006, 'ACTIVE', 0, CURRENT_TIMESTAMP(3), 0, 0),
    (100000000107, 100000000008, 100000000007, 'ACTIVE', 0, CURRENT_TIMESTAMP(3), 0, 0);

INSERT INTO edu_student (
    id, student_no, user_id, mobile, nickname, real_name, wx_openid,
    wx_unionid, wecom_external_user_id, primary_lead_id, status, tags,
    created_by, updated_by
) VALUES
    (100000000302, 'STU_S3_DEMO', 100000000009, '13900000009', 'S3 Student',
     'S3 Demo Student', 'wx_s3_demo_student', 'union_s3_demo_student',
     'external_s3_demo_student', null, 'ACTIVE', '["S3_DEMO"]', 0, 0);

INSERT INTO sys_config (
    id, config_group, config_key, display_name, config_value, masked_value,
    sensitive_flag, editable_flag, description, created_by, updated_by
) VALUES
    (100000000207, 'PURCHASE', 'PURCHASE_APPROVAL_THRESHOLD_CENT', '采购审批阈值（分）',
     '500000', '500000', 0, 1, '采购金额达到该阈值后进入审批；S3 仅提供配置基础', 0, 0),
    (100000000208, 'FILE', 'FILE_MAX_UPLOAD_SIZE_MB', '最大上传大小（MB）',
     '20', '20', 0, 1, '文件上传限制配置；S3 仅提供配置基础', 0, 0),
    (100000000209, 'NOTIFICATION', 'WECOM_CARD_MOCK_ENABLED', '企微卡片 Mock 开关',
     'true', 'true', 0, 1, '通知 Mock 与真实接入共用权限、配置和审计模型', 0, 0),
    (100000000210, 'NOTIFICATION', 'ROLE_APPLICATION_TEMPLATE', '角色申请通知模板',
     'ROLE_APPLICATION_MINIMAL', 'ROLE_APPLICATION_MINIMAL', 0, 1, 'S3 角色申请站内信和企微卡片 Mock 模板占位', 0, 0);
