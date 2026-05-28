package com.wecombft.application.crm;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class PromotionCodeService {

    private static final String CONFIG_GROUP = "PROMOTION";
    private static final List<String> CHANNELS = List.of(
        "XHS", "WECHAT_MOMENTS", "WECHAT_OFFICIAL", "DOUYIN", "OTHER");
    private static final char[] CODE_CHARS = "ABCDEFGHIJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final Random RANDOM = new Random();

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;

    public PromotionCodeService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
    }

    public List<PromotionCodeView> listAll() {
        List<PromotionCodeView> result = new ArrayList<>();
        jdbcTemplate.query(
            """
            select config_key, display_name, config_value, description, created_at
            from sys_config
            where config_group = ?
            order by created_at desc, id desc
            """,
            (rs, rowNum) -> {
                String code = rs.getString("config_key");
                String displayName = rs.getString("display_name");
                String configValue = rs.getString("config_value");
                String description = rs.getString("description");
                LocalDateTime createdAt = rs.getObject("created_at", LocalDateTime.class);
                result.add(new PromotionCodeView(
                    code,
                    displayName,
                    configValue == null ? "OTHER" : configValue,
                    description,
                    "https://finhub.tax/h5/lead/?code=" + code,
                    createdAt));
                return null;
            },
            CONFIG_GROUP);
        return result;
    }

    @Transactional
    public PromotionCodeView create(String name, String channel) {
        AdminPrincipal principal = AdminPrincipalContext.currentOrNull();
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少管理端登录态");
        }
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "推广码名称不能为空");
        }
        String safeChannel = channel == null ? "OTHER" : channel.trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(safeChannel)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
                "渠道编码非法，期望 " + String.join("/", CHANNELS));
        }
        String code = generateUniqueCode();
        long id = idGenerator.nextId();
        jdbcTemplate.update(
            """
            insert into sys_config (
                id, config_group, config_key, display_name, config_value, masked_value,
                sensitive_flag, editable_flag, description, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, 0, 1, ?, ?, ?)
            """,
            id,
            CONFIG_GROUP,
            code,
            name.trim(),
            safeChannel,
            safeChannel,
            "推广码：渠道=" + safeChannel + "，落地页=https://finhub.tax/h5/lead/?code=" + code,
            principal.userId(),
            principal.userId());
        auditLogService.writeSuccess(
            principal,
            "CRM",
            "PROMOTION_CODE_CREATE",
            "PROMOTION_CODE",
            id,
            code,
            null,
            "{\"channel\":\"" + safeChannel + "\",\"name\":\"" + name.trim() + "\"}");
        return new PromotionCodeView(
            code,
            name.trim(),
            safeChannel,
            "推广码：渠道=" + safeChannel + "，落地页=https://finhub.tax/h5/lead/?code=" + code,
            "https://finhub.tax/h5/lead/?code=" + code,
            LocalDateTime.now());
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            char[] buf = new char[8];
            for (int i = 0; i < buf.length; i++) {
                buf[i] = CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)];
            }
            String code = new String(buf);
            Integer exists = jdbcTemplate.queryForObject(
                "select count(*) from sys_config where config_group = ? and config_key = ?",
                Integer.class,
                CONFIG_GROUP,
                code);
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "推广码生成失败，请重试");
    }

    public record PromotionCodeView(
        String code,
        String name,
        String channel,
        String description,
        String landingUrl,
        LocalDateTime createdAt
    ) {
    }
}
