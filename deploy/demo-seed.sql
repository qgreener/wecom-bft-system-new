-- 演示数据 seed：补全课程/规格/课节/SKU/供货关联，让前台看着饱满。
-- 设计：用 200000xxxxx 段 ID 避免和 IdGenerator 雪花冲突。
-- 重复执行：用 INSERT IGNORE / DELETE WHERE id BETWEEN 安全段保证幂等。

START TRANSACTION;

-- =========================
-- 0) 清理之前的演示 seed（id 段 200000000xxx）
-- =========================
DELETE FROM course_lesson_node WHERE id BETWEEN 200000000000 AND 200000000999;
DELETE FROM course_spec        WHERE id BETWEEN 200000000000 AND 200000000999;
DELETE FROM supplier_sku       WHERE id BETWEEN 200000000000 AND 200000000999;
DELETE FROM inventory_sku      WHERE id BETWEEN 200000000100 AND 200000000199;
DELETE FROM tax_rule           WHERE id BETWEEN 200000000200 AND 200000000299;
DELETE FROM course             WHERE id BETWEEN 200000000300 AND 200000000399;

-- =========================
-- 1) 补两条税务规则
-- =========================
INSERT INTO tax_rule
    (id, rule_no, rule_name, tax_category, tax_rate, invoice_item_name, status, created_by, updated_by)
VALUES
    (200000000201, 'TAX_DEMO_MATERIAL', '教材资料 13%',  'GOODS',             0.1300, '*教材资料*', 'ACTIVE', 0, 0),
    (200000000202, 'TAX_DEMO_GIFT',     '实物赠品 13%',  'GOODS_PROMOTION',   0.1300, '*实物赠品*', 'ACTIVE', 0, 0);

-- =========================
-- 2) 补几个 SKU（用作课程的实物 / 赠品）
-- =========================
INSERT INTO inventory_sku
    (id, sku_no, sku_name, category_code, sku_type, unit,
     spec_attrs, spec_attrs_hash, default_supplier_id, cost_price_cent,
     current_stock, locked_stock, available_stock, safety_stock,
     status, image_url, created_by, updated_by, idempotency_key)
VALUES
    (200000000101, 'SKU_DEMO_PY_BOOK',     'Python 训练营教材',     'TEXTBOOK', 'MATERIAL', 'set',
     NULL, 'demo_py_book',     100000000301, 8000,
     200, 0, 200, 20, 'ACTIVE',
     'https://picsum.photos/seed/pybook/400/300', 0, 0, 'seed_sku_py_book'),
    (200000000102, 'SKU_DEMO_JAVA_BOOK',   'Java 实战教材',        'TEXTBOOK', 'MATERIAL', 'set',
     NULL, 'demo_java_book',   100000000301, 9000,
     150, 0, 150, 20, 'ACTIVE',
     'https://picsum.photos/seed/javabook/400/300', 0, 0, 'seed_sku_java_book'),
    (200000000103, 'SKU_DEMO_AI_KIT',      'AI Agent 实操礼盒',     'GIFTBOX',  'MATERIAL', 'set',
     NULL, 'demo_ai_kit',      100000000301, 12000,
     100, 0, 100, 10, 'ACTIVE',
     'https://picsum.photos/seed/aikit/400/300',  0, 0, 'seed_sku_ai_kit'),
    (200000000104, 'SKU_DEMO_GIFT_NOTE',   '训练营笔记本（赠品）',  'GIFTBOX',  'GIFT',     'piece',
     NULL, 'demo_gift_note',   100000000301, 1500,
     500, 0, 500, 50, 'ACTIVE',
     'https://picsum.photos/seed/notebook/400/300', 0, 0, 'seed_sku_gift_note');

-- =========================
-- 3) supplier_sku 关联（DEMO_S3 供货商供这几款 SKU）
-- =========================
INSERT INTO supplier_sku
    (id, supplier_id, sku_id, supplier_sku_no, purchase_price_cent, min_order_qty, lead_days, status, created_by, updated_by)
VALUES
    (200000000001, 100000000301, 100000000402, 'S_S4_TEXTBOOK', 6000,  5, 3, 'ACTIVE', 0, 0),
    (200000000002, 100000000301, 100000000403, 'S_S4_GIFT',     1200,  5, 3, 'ACTIVE', 0, 0),
    (200000000003, 100000000301, 200000000101, 'S_PY_BOOK',     6000, 10, 5, 'ACTIVE', 0, 0),
    (200000000004, 100000000301, 200000000102, 'S_JAVA_BOOK',   7000, 10, 5, 'ACTIVE', 0, 0),
    (200000000005, 100000000301, 200000000103, 'S_AI_KIT',      9000,  5, 7, 'ACTIVE', 0, 0),
    (200000000006, 100000000301, 200000000104, 'S_GIFT_NOTE',   1000, 20, 3, 'ACTIVE', 0, 0);

-- =========================
-- 4) 4 门课程（已上架 + 2 个规格 + 章节）
--    teacher_user_id = DEMO_TEACHER (100000000008)
--    default_tax_rule_id = TRAINING 6%
-- =========================
INSERT INTO course
    (id, course_no, course_title, course_type, cover_url, summary, detail,
     teacher_user_id, category_code, course_group_qr, default_tax_rule_id, status, sale_start_at, published_at,
     created_by, updated_by)
VALUES
    (200000000301, 'COURSE_DEMO_PY',
     'Python 全栈训练营', 'LIVE',
     'https://picsum.photos/seed/python/600/400',
     '从零基础到独立完成全栈项目的 12 周训练营，含直播+实战。',
     '<p>课程亮点：</p><ul><li>资深工程师主讲</li><li>每周一次实战项目</li><li>赠送学习礼盒</li></ul>',
     100000000008, 'TRAINING_BOOTCAMP',
     'https://picsum.photos/seed/pyqr/200/200',
     100000000401, 'ON_SHELF',
     NOW(3), NOW(3), 0, 0),

    (200000000302, 'COURSE_DEMO_JAVA',
     'Java 后端架构师特训', 'LIVE',
     'https://picsum.photos/seed/java/600/400',
     'Spring Boot 3 + 微服务 + 高并发实战，覆盖企业级真实场景。',
     '<p>10 周直播课程，配套真实生产案例。</p>',
     100000000008, 'TRAINING_BOOTCAMP',
     'https://picsum.photos/seed/javaqr/200/200',
     100000000401, 'ON_SHELF',
     NOW(3), NOW(3), 0, 0),

    (200000000303, 'COURSE_DEMO_AGENT',
     'AI Agent 工程化实战', 'LIVE',
     'https://picsum.photos/seed/agent/600/400',
     '从 LLM 接口调用到多 Agent 协作的工程化实操，配套实操礼盒。',
     '<p>8 周课程，含真实业务场景拆解和落地。</p>',
     100000000008, 'AI_PRACTICE',
     'https://picsum.photos/seed/agentqr/200/200',
     100000000401, 'ON_SHELF',
     NOW(3), NOW(3), 0, 0),

    (200000000304, 'COURSE_DEMO_FE',
     'Vue + React 前端进阶', 'RECORDED',
     'https://picsum.photos/seed/fe/600/400',
     '深入响应式原理、SSR、性能优化的录播课程。',
     '<p>共 32 节录播课节，可反复回看。</p>',
     100000000008, 'FRONTEND',
     NULL,
     100000000401, 'ON_SHELF',
     NOW(3), NOW(3), 0, 0);

-- =========================
-- 5) 课程规格 course_spec
--    每门课 2 个规格：基础（仅服务）+ 教材包（含实物 + 拆分）
-- =========================
INSERT INTO course_spec
    (id, spec_no, course_id, spec_name, sale_price_cent, origin_price_cent,
     stock_mode, contains_physical, sku_id, gift_sku_id, tax_rule_id,
     amount_split_snapshot, status, sort_no, created_by, updated_by)
VALUES
    -- Python
    (200000000401, 'SPEC_DEMO_PY_BASIC',  200000000301, '基础版',     49900,  59900,
     'UNLIMITED', 0, NULL, 200000000104, 100000000401,
     NULL, 'ENABLED', 10, 0, 0),
    (200000000402, 'SPEC_DEMO_PY_PACK',   200000000301, '教材包',    159900, 199900,
     'UNLIMITED', 1, 200000000101, 200000000104, 100000000401,
     CAST('{"items":[{"item":"培训服务","amount_cent":139900,"tax_rule_id":100000000401},{"item":"教材资料","amount_cent":20000,"tax_rule_id":200000000201}]}' AS JSON),
     'ENABLED', 20, 0, 0),

    -- Java
    (200000000403, 'SPEC_DEMO_JA_BASIC',  200000000302, '基础版',     69900,  79900,
     'UNLIMITED', 0, NULL, 200000000104, 100000000401,
     NULL, 'ENABLED', 10, 0, 0),
    (200000000404, 'SPEC_DEMO_JA_PACK',   200000000302, '教材包',    179900, 219900,
     'UNLIMITED', 1, 200000000102, 200000000104, 100000000401,
     CAST('{"items":[{"item":"培训服务","amount_cent":149900,"tax_rule_id":100000000401},{"item":"教材资料","amount_cent":30000,"tax_rule_id":200000000201}]}' AS JSON),
     'ENABLED', 20, 0, 0),

    -- AI Agent
    (200000000405, 'SPEC_DEMO_AI_BASIC',  200000000303, '标准版',     89900,  99900,
     'UNLIMITED', 0, NULL, 200000000104, 100000000401,
     NULL, 'ENABLED', 10, 0, 0),
    (200000000406, 'SPEC_DEMO_AI_KIT',    200000000303, '实操礼盒版', 199900, 239900,
     'UNLIMITED', 1, 200000000103, 200000000104, 100000000401,
     CAST('{"items":[{"item":"培训服务","amount_cent":169900,"tax_rule_id":100000000401},{"item":"实物礼盒","amount_cent":30000,"tax_rule_id":200000000202}]}' AS JSON),
     'ENABLED', 20, 0, 0),

    -- 前端
    (200000000407, 'SPEC_DEMO_FE_LITE',   200000000304, '轻享版',     29900,  39900,
     'UNLIMITED', 0, NULL, NULL, 100000000401,
     NULL, 'ENABLED', 10, 0, 0),
    (200000000408, 'SPEC_DEMO_FE_PRO',    200000000304, '完整版',     59900,  79900,
     'UNLIMITED', 0, NULL, NULL, 100000000401,
     NULL, 'ENABLED', 20, 0, 0);

-- =========================
-- 6) 课程章节小节
--    每门课：1 个章节 + 2 个小节
-- =========================
INSERT INTO course_lesson_node
    (id, course_id, parent_node_id, node_type, title, lesson_type,
     live_start_at, live_end_at, replay_url, resource_file,
     status, sort_no, remind_enabled, created_by, updated_by)
VALUES
    -- Python
    (200000000501, 200000000301, NULL,         'CHAPTER', '第 1 章 入门与环境', NULL,
     NULL, NULL, NULL, NULL, 'PUBLISHED', 10, 0, 0, 0),
    (200000000502, 200000000301, 200000000501, 'LESSON',  '1.1 Python 基础语法',  'LIVE',
     DATE_ADD(NOW(3), INTERVAL 3 DAY), DATE_ADD(NOW(3), INTERVAL 3 DAY) + INTERVAL 90 MINUTE,
     NULL, NULL, 'PUBLISHED', 11, 1, 0, 0),
    (200000000503, 200000000301, 200000000501, 'LESSON',  '1.2 项目结构实操',     'RECORDED',
     NULL, NULL, 'https://picsum.photos/seed/pyrep/600/400', NULL,
     'PUBLISHED', 12, 0, 0, 0),

    -- Java
    (200000000511, 200000000302, NULL,         'CHAPTER', '第 1 章 Spring Boot 3', NULL,
     NULL, NULL, NULL, NULL, 'PUBLISHED', 10, 0, 0, 0),
    (200000000512, 200000000302, 200000000511, 'LESSON',  '1.1 启动与自动配置',    'LIVE',
     DATE_ADD(NOW(3), INTERVAL 5 DAY), DATE_ADD(NOW(3), INTERVAL 5 DAY) + INTERVAL 90 MINUTE,
     NULL, NULL, 'PUBLISHED', 11, 1, 0, 0),
    (200000000513, 200000000302, 200000000511, 'LESSON',  '1.2 微服务拆分实战',    'RECORDED',
     NULL, NULL, 'https://picsum.photos/seed/jarep/600/400', NULL,
     'PUBLISHED', 12, 0, 0, 0),

    -- AI
    (200000000521, 200000000303, NULL,         'CHAPTER', '第 1 章 LLM 工程基础', NULL,
     NULL, NULL, NULL, NULL, 'PUBLISHED', 10, 0, 0, 0),
    (200000000522, 200000000303, 200000000521, 'LESSON',  '1.1 接口调用与 Prompt', 'LIVE',
     DATE_ADD(NOW(3), INTERVAL 7 DAY), DATE_ADD(NOW(3), INTERVAL 7 DAY) + INTERVAL 90 MINUTE,
     NULL, NULL, 'PUBLISHED', 11, 1, 0, 0),
    (200000000523, 200000000303, 200000000521, 'LESSON',  '1.2 多 Agent 协作',     'RECORDED',
     NULL, NULL, 'https://picsum.photos/seed/airep/600/400', NULL,
     'PUBLISHED', 12, 0, 0, 0),

    -- 前端
    (200000000531, 200000000304, NULL,         'CHAPTER', '第 1 章 响应式与 SSR', NULL,
     NULL, NULL, NULL, NULL, 'PUBLISHED', 10, 0, 0, 0),
    (200000000532, 200000000304, 200000000531, 'LESSON',  '1.1 Vue 3 响应式',     'RECORDED',
     NULL, NULL, 'https://picsum.photos/seed/ferep1/600/400', NULL,
     'PUBLISHED', 11, 0, 0, 0),
    (200000000533, 200000000304, 200000000531, 'LESSON',  '1.2 React Server Comp', 'RECORDED',
     NULL, NULL, 'https://picsum.photos/seed/ferep2/600/400', NULL,
     'PUBLISHED', 12, 0, 0, 0);

COMMIT;

-- 验证
SELECT '== courses ==' AS info;
SELECT id, course_no, course_title, course_type, status FROM course WHERE id BETWEEN 200000000300 AND 200000000399;
SELECT '== specs ==' AS info;
SELECT course_id, spec_name, sale_price_cent, contains_physical FROM course_spec WHERE id BETWEEN 200000000400 AND 200000000499 ORDER BY course_id, sort_no;
SELECT '== lessons ==' AS info;
SELECT course_id, node_type, title, status FROM course_lesson_node WHERE id BETWEEN 200000000500 AND 200000000599 ORDER BY course_id, sort_no;
SELECT '== sku ==' AS info;
SELECT id, sku_no, sku_name, current_stock FROM inventory_sku WHERE id BETWEEN 200000000100 AND 200000000199;
SELECT '== supplier_sku ==' AS info;
SELECT supplier_id, sku_id FROM supplier_sku WHERE id BETWEEN 200000000000 AND 200000000999;
