package com.wecombft.application.system;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.shared.web.ApiException;

/**
 * 系统设置 → 管理员账号列表（只读 + 启停）。
 * 角色权限矩阵走 PermissionCatalog 暴露；这里只做 sys_user / sys_user_role 的查询和状态切换。
 */
@Service
public class SystemAdminUserService {

    private final JdbcTemplate jdbcTemplate;

    public SystemAdminUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminUserView> list(String keyword, String status) {
        StringBuilder where = new StringBuilder("u.user_type = 'INTERNAL' and u.deleted_flag = 0");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (u.user_no like ? or u.display_name like ? or u.mobile like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and u.status = ?");
            args.add(status);
        }
        List<AdminUserView> users = jdbcTemplate.query(
            """
            select u.id, u.user_no, u.display_name, u.mobile, u.wecom_user_id,
                   u.status, u.last_login_at, u.created_at
            from sys_user u
            where %s
            order by u.created_at desc, u.id desc
            limit 200
            """.formatted(where),
            this::mapUser,
            args.toArray());
        if (users.isEmpty()) {
            return users;
        }
        // 一次性拉所有用户的角色，避免 N+1
        List<Long> userIds = users.stream().map(AdminUserView::id).toList();
        Map<Long, List<String>> roleMap = loadRoles(userIds);
        return users.stream().map(u -> u.withRoles(roleMap.getOrDefault(u.id(), List.of()))).toList();
    }

    @Transactional
    public AdminUserView setStatus(long userId, String targetStatus) {
        if (!"ACTIVE".equals(targetStatus) && !"DISABLED".equals(targetStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "状态仅支持 ACTIVE / DISABLED");
        }
        int rows = jdbcTemplate.update(
            """
            update sys_user
            set status = ?, updated_at = ?, version = version + 1
            where id = ? and user_type = 'INTERNAL' and deleted_flag = 0
            """,
            targetStatus,
            LocalDateTime.now(),
            userId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "管理员账号不存在");
        }
        return list(null, null).stream()
            .filter(u -> u.id() == userId)
            .findFirst()
            .orElseThrow();
    }

    /**
     * 覆盖式设置某账号的角色集合：把不在目标集合内的现有角色置为 REVOKED；目标集合内的角色若不存在则插入，
     * 若已 REVOKED 则恢复为 ACTIVE。事务内完成，避免空窗期。
     */
    @Transactional
    public AdminUserView assignRoles(long userId, long operatorUserId, List<String> targetRoleCodes) {
        if (targetRoleCodes == null) targetRoleCodes = List.of();
        // 1. 校验账号存在
        Integer userExists = jdbcTemplate.queryForObject(
            "select count(*) from sys_user where id = ? and user_type = 'INTERNAL' and deleted_flag = 0",
            Integer.class, userId);
        if (userExists == null || userExists == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "管理员账号不存在");
        }
        // 2. 解析角色 code -> id
        List<Map<String, Object>> roleRows = targetRoleCodes.isEmpty() ? List.of() :
            jdbcTemplate.queryForList(
                """
                select id, role_code from sys_role
                where status = 'ACTIVE' and deleted_flag = 0 and role_code in (%s)
                """.formatted(String.join(",", targetRoleCodes.stream().map(c -> "?").toList())),
                targetRoleCodes.toArray());
        if (roleRows.size() != targetRoleCodes.size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "存在无效的角色代码");
        }
        Map<String, Long> codeToId = new HashMap<>();
        for (Map<String, Object> row : roleRows) {
            codeToId.put((String) row.get("role_code"), ((Number) row.get("id")).longValue());
        }

        // 3. 当前 ACTIVE 角色 ids
        List<Long> currentRoleIds = jdbcTemplate.queryForList(
            "select role_id from sys_user_role where user_id = ? and grant_status = 'ACTIVE'",
            Long.class, userId);

        LocalDateTime now = LocalDateTime.now();
        // 4. 撤销不在目标集合中的角色
        for (Long oldRoleId : currentRoleIds) {
            if (!codeToId.containsValue(oldRoleId)) {
                jdbcTemplate.update(
                    """
                    update sys_user_role
                    set grant_status = 'REVOKED', revoked_by = ?, revoked_at = ?,
                        updated_by = ?, version = version + 1
                    where user_id = ? and role_id = ? and grant_status = 'ACTIVE'
                    """,
                    operatorUserId, now, operatorUserId, userId, oldRoleId);
            }
        }
        // 5. 目标集合内的角色：已有就恢复 ACTIVE，没有就插入
        for (Long newRoleId : codeToId.values()) {
            Integer existing = jdbcTemplate.queryForObject(
                "select count(*) from sys_user_role where user_id = ? and role_id = ?",
                Integer.class, userId, newRoleId);
            if (existing != null && existing > 0) {
                jdbcTemplate.update(
                    """
                    update sys_user_role
                    set grant_status = 'ACTIVE', granted_by = ?, granted_at = ?,
                        revoked_by = null, revoked_at = null,
                        updated_by = ?, version = version + 1
                    where user_id = ? and role_id = ?
                    """,
                    operatorUserId, now, operatorUserId, userId, newRoleId);
            } else {
                jdbcTemplate.update(
                    """
                    insert into sys_user_role (
                        id, user_id, role_id, grant_status, granted_by, granted_at,
                        created_by, updated_by
                    ) values (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                    """,
                    nextRoleGrantId(), userId, newRoleId, operatorUserId, now,
                    operatorUserId, operatorUserId);
            }
        }
        return list(null, null).stream()
            .filter(u -> u.id() == userId)
            .findFirst()
            .orElseThrow();
    }

    private long nextRoleGrantId() {
        // 简单做法：用毫秒时间戳 + 自增计数器，避开引入 IdGenerator 依赖
        return System.currentTimeMillis() * 1000L + grantCounter.incrementAndGet() % 1000L;
    }

    private final java.util.concurrent.atomic.AtomicLong grantCounter = new java.util.concurrent.atomic.AtomicLong(0);

    private Map<Long, List<String>> loadRoles(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", userIds.stream().map(id -> "?").toList());
        Map<Long, List<String>> result = new HashMap<>();
        jdbcTemplate.query(
            """
            select ur.user_id, r.role_code
            from sys_user_role ur
            join sys_role r on r.id = ur.role_id
            where ur.user_id in (%s) and ur.grant_status = 'ACTIVE'
              and r.status = 'ACTIVE' and r.deleted_flag = 0
            """.formatted(placeholders),
            (rs) -> {
                long uid = rs.getLong("user_id");
                String code = rs.getString("role_code");
                result.computeIfAbsent(uid, k -> new ArrayList<>()).add(code);
            },
            userIds.toArray());
        return result;
    }

    private AdminUserView mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AdminUserView(
            rs.getLong("id"),
            rs.getString("user_no"),
            rs.getString("display_name"),
            rs.getString("mobile"),
            rs.getString("wecom_user_id"),
            rs.getString("status"),
            rs.getObject("last_login_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class),
            List.of());
    }

    public record AdminUserView(
        long id,
        String userNo,
        String displayName,
        String mobile,
        String wecomUserId,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        List<String> roleCodes
    ) {
        public AdminUserView withRoles(List<String> roles) {
            return new AdminUserView(id, userNo, displayName, mobile, wecomUserId, status,
                lastLoginAt, createdAt, roles);
        }
    }
}
