package com.wecombft.infrastructure.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class PermissionCatalog {

    private final Map<String, RolePolicy> policies = buildPolicies();
    private final RolePolicy unassignedPolicy = new RolePolicy(
        "UNASSIGNED",
        "NONE",
        "未分配角色，仅允许提交角色申请",
        Set.of("iam:role-application:create"),
        List.of(new MenuPolicy("role.application", "角色申请", "home", 10)),
        List.of(new FieldMaskPolicy("student_mobile", "学员手机号", "MASK_ALL"))
    );

    public RolePolicy policyFor(String roleCode) {
        return policies.get(roleCode);
    }

    public PermissionView merge(List<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return PermissionView.from(unassignedPolicy);
        }

        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        LinkedHashMap<String, MenuPolicy> menus = new LinkedHashMap<>();
        LinkedHashMap<String, FieldMaskPolicy> masks = new LinkedHashMap<>();
        DataScopePolicy dataScope = new DataScopePolicy("NONE", "未授权数据范围");

        for (String roleCode : roleCodes) {
            RolePolicy policy = policies.get(roleCode);
            if (policy == null) {
                continue;
            }
            permissions.addAll(policy.permissionCodes());
            policy.menus().forEach(menu -> menus.putIfAbsent(menu.menuCode(), menu));
            policy.fieldMasks().forEach(mask -> masks.putIfAbsent(mask.fieldCode(), mask));
            dataScope = chooseWidest(dataScope, new DataScopePolicy(policy.dataScopeCode(), policy.dataScopeDescription()));
        }

        return new PermissionView(
            List.copyOf(permissions),
            dataScope,
            menus.values().stream().sorted(Comparator.comparingInt(MenuPolicy::sortNo)).toList(),
            new ArrayList<>(masks.values())
        );
    }

    public boolean hasPermission(List<String> roleCodes, String permissionCode) {
        return merge(roleCodes).permissionCodes().contains(permissionCode);
    }

    private DataScopePolicy chooseWidest(DataScopePolicy current, DataScopePolicy candidate) {
        List<String> order = List.of(
            "NONE",
            "OWN_COURSE",
            "OWN_OR_TEAM",
            "AFTER_SALE",
            "SUPPLY_CHAIN",
            "FINANCE_AUTHORIZED",
            "COURSE_ALL",
            "ALL"
        );
        int currentIndex = order.indexOf(current.scopeCode());
        int candidateIndex = order.indexOf(candidate.scopeCode());
        return candidateIndex > currentIndex ? candidate : current;
    }

    private Map<String, RolePolicy> buildPolicies() {
        Map<String, RolePolicy> result = new LinkedHashMap<>();
        result.put("SUPER_ADMIN", new RolePolicy(
            "SUPER_ADMIN",
            "ALL",
            "全量业务数据",
            Set.of(
                "system:config:read",
                "system:config:write",
                "system:audit:read",
                "iam:role-application:approve",
                "iam:role-grant",
                "iam:user:read",
                "course:spec:write",
                "course:lesson:write",
                "course:lesson:read",
                "course:approval:approve",
                "crm:lead:read",
                "crm:lead:write",
                "student:read",
                "learning:entitlement:read",
                "trade:order:read",
                "refund:review:write",
                "tax:invoice:write",
                "finance:reconciliation:write",
                "accounting:material:write",
                "inventory:sku:write",
                "purchase:order:write",
                "purchase:approval:approve",
                "fulfillment:shipment:write",
                "system:logistics-config:read"
            ),
            List.of(
                new MenuPolicy("home.dashboard", "首页", null, 10),
                new MenuPolicy("crm.leads", "线索管理", "crm", 100),
                new MenuPolicy("crm.students", "学员管理", "crm", 110),
                new MenuPolicy("course.manage", "课程管理", "course", 120),
                new MenuPolicy("course.lesson-content", "课节内容", "course", 130),
                new MenuPolicy("inventory.skus", "库存管理", "supply", 200),
                new MenuPolicy("purchase.orders", "采购订单", "supply", 210),
                new MenuPolicy("fulfillment.shipments", "发货管理", "supply", 220),
                new MenuPolicy("trade.orders", "订单管理", "trade", 300),
                new MenuPolicy("refund.reviews", "退款处理", "trade", 310),
                new MenuPolicy("invoice.manage", "开票管理", "finance", 400),
                new MenuPolicy("finance.reconciliation", "收款对账", "finance", 410),
                new MenuPolicy("accounting.workspace", "代账管理", "finance", 420),
                new MenuPolicy("system.settings", "系统设置", "system", 900),
                new MenuPolicy("system.audit-logs", "操作日志", "system", 910)
            ),
            List.of(
                new FieldMaskPolicy("student_mobile", "学员手机号", "PLAIN"),
                new FieldMaskPolicy("invoice_tax_no", "发票税号", "PLAIN"),
                new FieldMaskPolicy("config_secret", "平台密钥", "MASKED_ONLY")
            )
        ));
        result.put("OPS", new RolePolicy(
            "OPS",
            "OWN_OR_TEAM",
            "本人或团队运营线索",
            Set.of(
                "crm:lead:read",
                "crm:lead:write",
                "student:read",
                "trade:order:read",
                "report:business:read"
            ),
            List.of(
                new MenuPolicy("home.dashboard", "首页", null, 10),
                new MenuPolicy("crm.leads", "线索管理", "crm", 100),
                new MenuPolicy("crm.students", "学员管理", "crm", 110),
                new MenuPolicy("trade.orders", "订单管理", "trade", 300),
                new MenuPolicy("report.business", "业务报表", "report", 700)
            ),
            List.of(
                new FieldMaskPolicy("student_mobile", "学员手机号", "OWN_FULL_OTHER_MASK"),
                new FieldMaskPolicy("lead_mobile", "线索手机号", "OWN_FULL_OTHER_MASK"),
                new FieldMaskPolicy("invoice_tax_no", "发票税号", "HIDE")
            )
        ));
        result.put("EDU_ADMIN", new RolePolicy(
            "EDU_ADMIN",
            "COURSE_ALL",
            "全部课程、课节和学习数据范围",
            Set.of(
                "course:spec:write",
                "course:lesson:write",
                "course:lesson:read",
                "course:approval:submit",
                "learning:entitlement:read",
                "student:read",
                "trade:order:read",
                "report:learning:read"
            ),
            List.of(
                new MenuPolicy("home.dashboard", "首页", null, 10),
                new MenuPolicy("course.manage", "课程管理", "course", 120),
                new MenuPolicy("course.lesson-content", "课节内容", "course", 130),
                new MenuPolicy("trade.orders", "订单管理", "trade", 300),
                new MenuPolicy("report.learning", "学习报表", "report", 710)
            ),
            List.of(
                new FieldMaskPolicy("student_mobile", "学员手机号", "MASKED_ONLY"),
                new FieldMaskPolicy("learning_record", "学习记录", "LEARNING_RECORD_FULL"),
                new FieldMaskPolicy("invoice_tax_no", "发票税号", "HIDE")
            )
        ));
        result.put("TEACHER", new RolePolicy(
            "TEACHER",
            "OWN_COURSE",
            "本人负责课程、课节和学习记录范围",
            Set.of(
                "course:lesson:write",
                "course:lesson:read",
                "learning:record:read",
                "learning:entitlement:read"
            ),
            List.of(
                new MenuPolicy("home.dashboard", "首页", null, 10),
                new MenuPolicy("course.lesson-content", "课节内容", "course", 130)
            ),
            List.of(
                new FieldMaskPolicy("student_mobile", "学员手机号", "MASKED_ONLY"),
                new FieldMaskPolicy("learning_record", "学习记录", "LEARNING_RECORD_FULL"),
                new FieldMaskPolicy("invoice_tax_no", "发票税号", "HIDE")
            )
        ));
        result.put("SERVICE", new RolePolicy(
            "SERVICE",
            "AFTER_SALE",
            "售后相关学员、订单、退款和开票范围",
            Set.of(
                "student:read",
                "trade:order:read",
                "refund:review:write",
                "invoice:read"
            ),
            List.of(
                new MenuPolicy("home.dashboard", "首页", null, 10),
                new MenuPolicy("trade.orders", "订单管理", "trade", 300),
                new MenuPolicy("refund.reviews", "退款处理", "trade", 310),
                new MenuPolicy("invoice.readonly", "开票查询", "trade", 320)
            ),
            List.of(
                new FieldMaskPolicy("student_mobile", "学员手机号", "AFTER_SALE_FULL"),
                new FieldMaskPolicy("receiver_address", "收货地址", "AFTER_SALE_NEEDED_FULL"),
                new FieldMaskPolicy("invoice_tax_no", "发票税号", "MASKED_ONLY")
            )
        ));
        result.put("WAREHOUSE", new RolePolicy(
            "WAREHOUSE",
            "SUPPLY_CHAIN",
            "供应链、库存和含实物订单范围",
            Set.of(
                "inventory:sku:write",
                "purchase:order:write",
                "fulfillment:shipment:write",
                "trade:order:read",
                "system:logistics-config:read"
            ),
            List.of(
                new MenuPolicy("home.dashboard", "首页", null, 10),
                new MenuPolicy("inventory.skus", "库存管理", "supply", 200),
                new MenuPolicy("purchase.orders", "采购订单", "supply", 210),
                new MenuPolicy("fulfillment.shipments", "发货管理", "supply", 220),
                new MenuPolicy("trade.orders", "订单管理", "trade", 300)
            ),
            List.of(
                new FieldMaskPolicy("receiver_mobile", "收货手机号", "SHIPPING_ONLY_FULL"),
                new FieldMaskPolicy("receiver_address", "收货地址", "SHIPPING_ONLY_FULL"),
                new FieldMaskPolicy("paid_amount_cent", "订单金额", "HIDE")
            )
        ));
        result.put("ACCOUNTING", new RolePolicy(
            "ACCOUNTING",
            "FINANCE_AUTHORIZED",
            "授权财税模块、月份和单据范围",
            Set.of(
                "tax:invoice:write",
                "finance:reconciliation:write",
                "accounting:material:write",
                "trade:order:read",
                "system:tax-config:read"
            ),
            List.of(
                new MenuPolicy("home.dashboard", "首页", null, 10),
                new MenuPolicy("trade.orders", "订单管理", "trade", 300),
                new MenuPolicy("invoice.manage", "开票管理", "finance", 400),
                new MenuPolicy("finance.reconciliation", "收款对账", "finance", 410),
                new MenuPolicy("accounting.workspace", "代账管理", "finance", 420)
            ),
            List.of(
                new FieldMaskPolicy("student_mobile", "学员手机号", "MASKED_ONLY"),
                new FieldMaskPolicy("invoice_tax_no", "发票税号", "FINANCE_ONLY_FULL"),
                new FieldMaskPolicy("paid_amount_cent", "订单金额", "FINANCE_ONLY_FULL")
            )
        ));
        return Map.copyOf(result);
    }

    public record RolePolicy(
        String roleCode,
        String dataScopeCode,
        String dataScopeDescription,
        Set<String> permissionCodes,
        List<MenuPolicy> menus,
        List<FieldMaskPolicy> fieldMasks
    ) {
    }

    public record PermissionView(
        List<String> permissionCodes,
        DataScopePolicy dataScope,
        List<MenuPolicy> menus,
        List<FieldMaskPolicy> fieldMasks
    ) {
        static PermissionView from(RolePolicy policy) {
            return new PermissionView(
                policy.permissionCodes().stream().sorted().toList(),
                new DataScopePolicy(policy.dataScopeCode(), policy.dataScopeDescription()),
                policy.menus(),
                policy.fieldMasks()
            );
        }
    }

    public record DataScopePolicy(String scopeCode, String description) {
    }

    public record MenuPolicy(String menuCode, String menuName, String parentCode, int sortNo) {
    }

    public record FieldMaskPolicy(String fieldCode, String displayName, String maskStrategy) {
    }
}
