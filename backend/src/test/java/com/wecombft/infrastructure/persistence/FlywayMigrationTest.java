package com.wecombft.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_apply_infrastructure_baseline_migration() {
        Boolean tableExists = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(null, null, "sys_app_metadata", new String[] {"TABLE"})) {
                return tables.next();
            }
        });

        assertThat(tableExists).isTrue();
    }

    @Test
    void should_apply_core_domain_schema_migration() {
        assertThat(existingTables()).containsAll(List.of(
                "sys_user",
                "sys_role",
                "sys_user_role",
                "sys_config",
                "crm_lead",
                "edu_student",
                "student_address",
                "student_invoice_title",
                "course",
                "course_spec",
                "course_lesson_node",
                "learning_entitlement",
                "trade_order",
                "trade_order_item",
                "order_document_link",
                "pay_payment",
                "pay_refund",
                "pay_refund_item",
                "fulfillment_shipment",
                "fulfillment_shipment_item",
                "logistics_trace",
                "inventory_sku",
                "inventory_stock_flow",
                "supplier",
                "supplier_sku",
                "purchase_order",
                "purchase_order_item",
                "purchase_receipt",
                "tax_rule",
                "tax_invoice",
                "tax_invoice_item",
                "finance_reconciliation_batch",
                "finance_reconciliation_record",
                "acct_material",
                "acct_material_relation",
                "notify_message",
                "approval_record",
                "sys_file_asset",
                "integration_callback_event",
                "audit_operation_log"));
    }

    @Test
    void should_keep_order_state_split_and_amount_cent_contract() {
        Set<String> orderColumns = existingColumns("trade_order");

        assertThat(orderColumns).doesNotContain("order" + "_status");
        assertThat(orderColumns).contains(
                "payment_status",
                "fulfillment_status",
                "refund_status",
                "invoice_status");

        Map<String, List<String>> moneyColumns = Map.ofEntries(
                Map.entry("trade_order", List.of("total_amount_cent", "discount_amount_cent", "payable_amount_cent", "paid_amount_cent")),
                Map.entry("trade_order_item", List.of("unit_price_cent", "total_amount_cent", "discount_amount_cent", "payable_amount_cent", "paid_amount_cent")),
                Map.entry("pay_payment", List.of("paid_amount_cent")),
                Map.entry("pay_refund", List.of("apply_amount_cent", "approved_amount_cent")),
                Map.entry("pay_refund_item", List.of("refund_amount_cent")),
                Map.entry("order_document_link", List.of("amount_cent")),
                Map.entry("inventory_sku", List.of("cost_price_cent")),
                Map.entry("purchase_order", List.of("total_amount_cent", "threshold_snapshot_cent", "input_invoice_amount_cent")),
                Map.entry("purchase_order_item", List.of("unit_price_cent", "total_amount_cent")),
                Map.entry("tax_invoice", List.of("invoice_amount_cent", "tax_amount_cent")),
                Map.entry("tax_invoice_item", List.of("amount_cent", "tax_amount_cent")),
                Map.entry("finance_reconciliation_record", List.of("system_amount_cent", "bill_amount_cent", "fee_amount_cent")),
                Map.entry("acct_material_relation", List.of("amount_cent")),
                Map.entry("approval_record", List.of("amount_snapshot_cent")));

        moneyColumns.forEach((table, columns) -> columns.forEach(column -> assertColumnType(table, column, "BIGINT")));
    }

    @Test
    void should_create_idempotency_unique_keys_and_document_chain_indexes() {
        assertThat(existingIndexes("trade_order")).contains("uk_trade_order_no", "uk_trade_merchant_order_no");
        assertThat(existingIndexes("pay_payment")).contains("uk_payment_idempotency", "uk_payment_external_no");
        assertThat(existingIndexes("pay_refund")).contains("uk_refund_idempotency", "uk_refund_external_no");
        assertThat(existingIndexes("integration_callback_event")).contains(
                "uk_callback_source_event",
                "uk_callback_idempotency",
                "idx_callback_order");
        assertThat(existingIndexes("order_document_link")).contains(
                "uk_doc_link_order_document",
                "idx_doc_link_order_type_time",
                "idx_doc_link_document");
        assertThat(existingIndexes("audit_operation_log")).contains("idx_audit_order", "idx_audit_target");
        assertThat(existingIndexes("sys_config")).contains("uk_sys_config_group_key", "idx_sys_config_group");
    }

    @Test
    void should_seed_s3_security_roles_users_and_masked_configs() {
        Integer roleCount = jdbcTemplate.queryForObject(
                """
                select count(*) from sys_role
                where role_code in ('SUPER_ADMIN', 'EDU_ADMIN', 'TEACHER', 'OPS', 'SERVICE', 'WAREHOUSE', 'ACCOUNTING')
                """,
                Integer.class);
        Integer demoUserCount = jdbcTemplate.queryForObject(
                """
                select count(*) from sys_user
                where user_no in (
                    'DEMO_ADMIN', 'DEMO_EDU_ADMIN', 'DEMO_TEACHER', 'DEMO_OPS',
                    'DEMO_SERVICE', 'DEMO_WAREHOUSE', 'DEMO_ACCOUNTING',
                    'DEMO_UNASSIGNED', 'DEMO_APP_STUDENT', 'DEMO_SUPPLIER'
                )
                """,
                Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject(
                """
                select count(*) from sys_user_role
                where grant_status = 'ACTIVE'
                """,
                Integer.class);
        Integer sensitiveConfigCount = jdbcTemplate.queryForObject(
                """
                select count(*) from sys_config
                where sensitive_flag = 1
                  and masked_value is not null
                  and (config_value is null or config_value not like '%secret%')
                """,
                Integer.class);

        assertThat(roleCount).isEqualTo(7);
        assertThat(demoUserCount).isEqualTo(10);
        assertThat(grantCount).isGreaterThanOrEqualTo(7);
        assertThat(sensitiveConfigCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    void should_seed_s3_gap_completion_identity_and_config_data() {
        Integer studentCount = jdbcTemplate.queryForObject(
                "select count(*) from edu_student where student_no = 'STU_S3_DEMO'",
                Integer.class);
        Integer supplierCount = jdbcTemplate.queryForObject(
                "select count(*) from supplier where supplier_no = 'SUP_S3_DEMO'",
                Integer.class);
        Integer configCount = jdbcTemplate.queryForObject(
                """
                select count(*) from sys_config
                where (config_group = 'PURCHASE' and config_key = 'PURCHASE_APPROVAL_THRESHOLD_CENT')
                   or (config_group = 'FILE' and config_key = 'FILE_MAX_UPLOAD_SIZE_MB')
                   or (config_group = 'NOTIFICATION' and config_key = 'WECOM_CARD_MOCK_ENABLED')
                """,
                Integer.class);

        assertThat(studentCount).isEqualTo(1);
        assertThat(supplierCount).isEqualTo(1);
        assertThat(configCount).isEqualTo(3);
    }

    @Test
    void should_apply_s4_foundation_schema_and_seed_data() {
        assertThat(existingTables()).contains("lead_follow_record");
        assertThat(existingColumns("approval_record")).contains("related_object_status_before");
        assertThat(existingIndexes("lead_follow_record")).contains(
                "uk_lead_follow_idempotency",
                "idx_lead_follow_lead_time",
                "idx_lead_follow_follower_time");

        Integer taxRuleCount = jdbcTemplate.queryForObject(
                "select count(*) from tax_rule where rule_no = 'TAX_S4_TRAINING' and status = 'ACTIVE'",
                Integer.class);
        Integer skuCount = jdbcTemplate.queryForObject(
                "select count(*) from inventory_sku where sku_no in ('SKU_S4_TEXTBOOK', 'SKU_S4_GIFT') and status = 'ACTIVE'",
                Integer.class);
        Integer studentCount = jdbcTemplate.queryForObject(
                "select count(*) from edu_student where student_no in ('STU_S4_MANUAL', 'STU_S4_DISABLED')",
                Integer.class);

        assertThat(taxRuleCount).isEqualTo(1);
        assertThat(skuCount).isEqualTo(2);
        assertThat(studentCount).isEqualTo(2);
    }

    @Test
    void should_not_add_soft_delete_columns_to_immutable_documents_logs_or_callbacks() {
        List<String> immutableTables = List.of(
                "trade_order",
                "trade_order_item",
                "order_document_link",
                "pay_payment",
                "pay_refund",
                "pay_refund_item",
                "fulfillment_shipment",
                "fulfillment_shipment_item",
                "logistics_trace",
                "inventory_stock_flow",
                "purchase_order",
                "purchase_order_item",
                "purchase_receipt",
                "tax_invoice",
                "tax_invoice_item",
                "finance_reconciliation_batch",
                "finance_reconciliation_record",
                "acct_material",
                "acct_material_relation",
                "notify_message",
                "approval_record",
                "sys_file_asset",
                "integration_callback_event",
                "audit_operation_log");

        immutableTables.forEach(table -> assertThat(existingColumns(table))
                .doesNotContain("deleted_flag", "deleted_at", "deleted_by"));
    }

    private Set<String> existingTables() {
        return jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            Set<String> tables = new java.util.HashSet<>();
            try (ResultSet resultSet = metaData.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("TABLE_NAME").toLowerCase());
                }
            }
            return tables;
        });
    }

    private Set<String> existingColumns(String tableName) {
        return jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            Set<String> columns = new java.util.HashSet<>();
            try (ResultSet resultSet = metaData.getColumns(null, null, tableName, "%")) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString("COLUMN_NAME").toLowerCase());
                }
            }
            return columns;
        });
    }

    private Set<String> existingIndexes(String tableName) {
        return jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            Set<String> indexes = new java.util.HashSet<>();
            try (ResultSet resultSet = metaData.getIndexInfo(null, null, tableName, false, false)) {
                while (resultSet.next()) {
                    String indexName = resultSet.getString("INDEX_NAME");
                    if (indexName != null) {
                        indexes.add(normalizeIndexName(indexName));
                    }
                }
            }
            return indexes;
        });
    }

    private String normalizeIndexName(String indexName) {
        return indexName.toLowerCase().replaceFirst("_index_[a-z0-9]+$", "");
    }

    private void assertColumnType(String tableName, String columnName, String expectedType) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            String actualType = findColumnType(connection, tableName, columnName);
            assertThat(actualType).as(tableName + "." + columnName).isEqualTo(expectedType);
            return null;
        });
    }

    private String findColumnType(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName)) {
            assertThat(resultSet.next()).as(tableName + "." + columnName + " exists").isTrue();
            return resultSet.getString("TYPE_NAME").toUpperCase();
        }
    }
}
