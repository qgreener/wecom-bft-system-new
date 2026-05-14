-- Demo-safe optional seed for local or demo databases only.
-- Requires V202605141930__create_core_domain_schema.sql.
-- This file is repeatable on MySQL: it uses stable demo business codes and
-- ON DUPLICATE KEY UPDATE. It must not be added to the production Flyway path.

INSERT INTO sys_role (
    id,
    role_code,
    role_name,
    role_type,
    data_scope,
    permission_codes,
    status,
    sort_no,
    description,
    created_by,
    updated_by
) VALUES
    (
        2000000000000000401,
        'S2_DEMO_ADMIN',
        'S2 Demo Admin',
        'INTERNAL',
        'ALL',
        JSON_ARRAY('menu:system', 'menu:course', 'menu:trade', 'system:audit:read', 'mock:scenario:read'),
        'ACTIVE',
        10,
        'S2 demo-safe role seed; S3 replaces this with formal permission seed.',
        NULL,
        NULL
    ),
    (
        2000000000000000402,
        'S2_DEMO_OPERATOR',
        'S2 Demo Operator',
        'INTERNAL',
        'OWN_OR_TEAM',
        JSON_ARRAY('menu:crm', 'menu:course', 'crm:lead:read', 'course:read'),
        'ACTIVE',
        20,
        'S2 demo-safe operator role seed.',
        NULL,
        NULL
    )
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    role_type = VALUES(role_type),
    data_scope = VALUES(data_scope),
    permission_codes = VALUES(permission_codes),
    status = VALUES(status),
    sort_no = VALUES(sort_no),
    description = VALUES(description),
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = VALUES(updated_by);

INSERT INTO sys_user (
    id,
    user_no,
    user_type,
    display_name,
    mobile,
    status,
    created_by,
    updated_by
) VALUES
    (
        2000000000000000411,
        'S2_DEMO_ADMIN_USER',
        'INTERNAL',
        'S2 Demo Admin User',
        '00000000001',
        'ACTIVE',
        NULL,
        NULL
    ),
    (
        2000000000000000412,
        'S2_DEMO_OPERATOR_USER',
        'INTERNAL',
        'S2 Demo Operator User',
        '00000000002',
        'ACTIVE',
        NULL,
        NULL
    )
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    mobile = VALUES(mobile),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = VALUES(updated_by);

INSERT INTO sys_user_role (
    id,
    user_id,
    role_id,
    grant_status,
    granted_by,
    granted_at,
    created_by,
    updated_by
) VALUES
    (
        2000000000000000421,
        2000000000000000411,
        2000000000000000401,
        'ACTIVE',
        NULL,
        CURRENT_TIMESTAMP(3),
        NULL,
        NULL
    ),
    (
        2000000000000000422,
        2000000000000000412,
        2000000000000000402,
        'ACTIVE',
        NULL,
        CURRENT_TIMESTAMP(3),
        NULL,
        NULL
    )
ON DUPLICATE KEY UPDATE
    grant_status = VALUES(grant_status),
    granted_by = VALUES(granted_by),
    granted_at = VALUES(granted_at),
    revoked_by = NULL,
    revoked_at = NULL,
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = VALUES(updated_by);

INSERT INTO tax_rule (
    id,
    rule_no,
    rule_name,
    tax_category,
    tax_rate,
    invoice_item_name,
    status,
    created_by,
    updated_by
) VALUES (
    2000000000000000001,
    'TAX_DEMO_TRAINING',
    'Demo training service tax rule',
    'TRAINING_SERVICE',
    0.0600,
    'Demo training service',
    'ENABLED',
    NULL,
    NULL
)
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    tax_category = VALUES(tax_category),
    tax_rate = VALUES(tax_rate),
    invoice_item_name = VALUES(invoice_item_name),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = VALUES(updated_by);

INSERT INTO supplier (
    id,
    supplier_no,
    supplier_name,
    short_name,
    contact_name,
    contact_mobile,
    contact_email,
    access_status,
    status,
    created_by,
    updated_by
) VALUES (
    2000000000000000101,
    'SUP_DEMO_001',
    'Demo Placeholder Supplier',
    'Demo Supplier',
    'Demo Contact',
    '00000000000',
    'demo-supplier@example.invalid',
    'ENABLED',
    'ENABLED',
    NULL,
    NULL
)
ON DUPLICATE KEY UPDATE
    supplier_name = VALUES(supplier_name),
    short_name = VALUES(short_name),
    contact_name = VALUES(contact_name),
    contact_mobile = VALUES(contact_mobile),
    contact_email = VALUES(contact_email),
    access_status = VALUES(access_status),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = VALUES(updated_by);

INSERT INTO inventory_sku (
    id,
    sku_no,
    sku_name,
    category_code,
    sku_type,
    unit,
    spec_attrs,
    spec_attrs_hash,
    default_supplier_id,
    cost_price_cent,
    current_stock,
    locked_stock,
    available_stock,
    safety_stock,
    status,
    created_by,
    updated_by
) VALUES (
    2000000000000000201,
    'SKU_DEMO_TEXTBOOK',
    'Demo Placeholder Textbook',
    'TEXTBOOK',
    'PHYSICAL_GOODS',
    'pcs',
    JSON_OBJECT('version', 'demo'),
    'DEMO_TEXTBOOK_VERSION',
    2000000000000000101,
    1000,
    20,
    0,
    20,
    5,
    'ENABLED',
    NULL,
    NULL
)
ON DUPLICATE KEY UPDATE
    sku_name = VALUES(sku_name),
    category_code = VALUES(category_code),
    sku_type = VALUES(sku_type),
    unit = VALUES(unit),
    spec_attrs = VALUES(spec_attrs),
    spec_attrs_hash = VALUES(spec_attrs_hash),
    default_supplier_id = VALUES(default_supplier_id),
    cost_price_cent = VALUES(cost_price_cent),
    current_stock = VALUES(current_stock),
    locked_stock = VALUES(locked_stock),
    available_stock = VALUES(available_stock),
    safety_stock = VALUES(safety_stock),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = VALUES(updated_by);

INSERT INTO course (
    id,
    course_no,
    course_title,
    course_type,
    summary,
    default_tax_rule_id,
    status,
    created_by,
    updated_by
) VALUES (
    2000000000000000301,
    'COURSE_DEMO_001',
    'Demo Placeholder Course',
    'RECORDED',
    'Demo-only course seed for local verification.',
    2000000000000000001,
    'DRAFT',
    NULL,
    NULL
)
ON DUPLICATE KEY UPDATE
    course_title = VALUES(course_title),
    course_type = VALUES(course_type),
    summary = VALUES(summary),
    default_tax_rule_id = VALUES(default_tax_rule_id),
    status = VALUES(status),
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = VALUES(updated_by);

INSERT INTO course_spec (
    id,
    spec_no,
    course_id,
    spec_name,
    sale_price_cent,
    origin_price_cent,
    stock_mode,
    contains_physical,
    sku_id,
    tax_rule_id,
    amount_split_snapshot,
    status,
    sort_no,
    created_by,
    updated_by
) VALUES (
    2000000000000000302,
    'SPEC_DEMO_001',
    2000000000000000301,
    'Demo standard spec',
    19900,
    29900,
    'LIMITED',
    1,
    2000000000000000201,
    2000000000000000001,
    JSON_OBJECT('training_amount_cent', 19900),
    'ENABLED',
    1,
    NULL,
    NULL
)
ON DUPLICATE KEY UPDATE
    course_id = VALUES(course_id),
    spec_name = VALUES(spec_name),
    sale_price_cent = VALUES(sale_price_cent),
    origin_price_cent = VALUES(origin_price_cent),
    stock_mode = VALUES(stock_mode),
    contains_physical = VALUES(contains_physical),
    sku_id = VALUES(sku_id),
    tax_rule_id = VALUES(tax_rule_id),
    amount_split_snapshot = VALUES(amount_split_snapshot),
    status = VALUES(status),
    sort_no = VALUES(sort_no),
    updated_at = CURRENT_TIMESTAMP(3),
    updated_by = VALUES(updated_by);
