package com.wecombft.application.purchase;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

/**
 * 采购单分享给供货商的"邀请链接"——用 finhub.tax 替代企微上下游链消息：
 * - PC 端点击「生成推送链接」→ 创建 invite token（限定 supplier_no + purchase_no + 过期时间）
 * - 管理员把 https://finhub.tax/h5/supplier/?invite=<token> 在企微会话粘贴给供货商对接人
 * - 供货商打开 H5，自动用 invite token 换取 session_token，登录后只能操作该 supplier_no
 *
 * 真实接入企微上下游链消息时，本服务无需改造业务模型，只需把"发送链接"的方式
 * 从"管理员复制"改为"调用企微 API 发送链消息卡片"。
 */
@Service
public class PurchaseInviteService {

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final String LANDING_BASE = "https://finhub.tax/h5/supplier/";

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;

    // 演示口径：用进程内存缓存 token 映射；生产可替换为 sys_config / Redis 持久化
    private final ConcurrentHashMap<String, InviteEntry> store = new ConcurrentHashMap<>();

    public PurchaseInviteService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
    }

    public ShareLinkView createShareLink(AdminPrincipal principal, long purchaseId) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少管理端登录态");
        }
        PurchaseSnapshot snap = loadPurchase(purchaseId);
        String token = encodeToken(idGenerator.nextId());
        long expireAt = Instant.now().plus(DEFAULT_TTL).getEpochSecond();
        store.put(token, new InviteEntry(snap.supplierNo(), snap.purchaseNo(), expireAt));
        purgeExpired();
        String landingUrl = LANDING_BASE + "?invite=" + token + "&purchase_no=" + snap.purchaseNo();
        auditLogService.writeSuccess(
            principal,
            "PURCHASE",
            "PURCHASE_SHARE_LINK_CREATE",
            "PURCHASE_ORDER",
            purchaseId,
            snap.purchaseNo(),
            null,
            "{\"supplier_no\":\"" + snap.supplierNo() + "\",\"expire_at\":" + expireAt + "}");
        return new ShareLinkView(token, landingUrl, snap.supplierNo(), snap.purchaseNo(), expireAt);
    }

    /**
     * 验证供货商 H5 提交的 invite token。返回 token 锁定的 supplier_no。
     */
    public Optional<InviteEntry> resolveInvite(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        InviteEntry entry = store.get(token);
        if (entry == null) return Optional.empty();
        if (entry.expireAt() < Instant.now().getEpochSecond()) {
            store.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private PurchaseSnapshot loadPurchase(long purchaseId) {
        return jdbcTemplate.query(
            """
            select p.purchase_no, s.supplier_no
            from purchase_order p
            left join supplier s on s.id = p.supplier_id
            where p.id = ? and p.deleted_flag = 0
            """,
            rs -> {
                if (!rs.next()) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "采购单不存在");
                }
                String supplierNo = rs.getString("supplier_no");
                if (supplierNo == null || supplierNo.isBlank()) {
                    throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "采购单未关联供货商，无法生成分享链接");
                }
                return new PurchaseSnapshot(rs.getString("purchase_no"), supplierNo);
            },
            purchaseId);
    }

    private void purgeExpired() {
        long now = Instant.now().getEpochSecond();
        store.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }

    private String encodeToken(long id) {
        // 8 字节 ID + 8 字节随机；Base64 URL-safe
        long random = (long) (Math.random() * Long.MAX_VALUE);
        byte[] buf = new byte[16];
        writeLong(buf, 0, id);
        writeLong(buf, 8, random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static void writeLong(byte[] buf, int off, long v) {
        for (int i = 0; i < 8; i++) {
            buf[off + i] = (byte) ((v >>> ((7 - i) * 8)) & 0xff);
        }
    }

    public record InviteEntry(String supplierNo, String purchaseNo, long expireAt) {
    }

    public record ShareLinkView(String token, String landingUrl, String supplierNo, String purchaseNo, long expireAt) {
    }

    private record PurchaseSnapshot(String purchaseNo, String supplierNo) {
    }

    // 静态导入辅助，保证 UTF_8 字段（虽然现在没用上，但保留以兼容未来 HMAC 签名扩展）
    @SuppressWarnings("unused")
    private static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}
