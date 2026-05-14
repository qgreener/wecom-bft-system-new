-- Demo-safe optional seed for local or demo databases only.
-- Requires V202605141930__create_core_domain_schema.sql.

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
);

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
);

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
);

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
);

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
);
