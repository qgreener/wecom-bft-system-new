package com.wecombft.application.iam;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.wecombft.application.purchase.PurchaseInviteService;
import com.wecombft.application.purchase.PurchaseInviteService.InviteEntry;
import com.wecombft.infrastructure.persistence.iam.AuthBoundaryRepository;
import com.wecombft.infrastructure.persistence.iam.AuthBoundaryRepository.AppStudentRecord;
import com.wecombft.infrastructure.persistence.iam.AuthBoundaryRepository.SupplierAuthRecord;
import com.wecombft.shared.web.ApiException;

import com.wecombft.application.command.iam.AppWechatLoginCommand;
import com.wecombft.application.command.iam.SupplierH5TokenCommand;
import com.wecombft.interfaces.dto.iam.AppWechatLoginResponse;
import com.wecombft.interfaces.dto.iam.SupplierH5TokenResponse;

@Service
public class ExternalAuthApplicationService {

    private static final String INVITE_PREFIX = "invite:";

    private final AuthBoundaryRepository authBoundaryRepository;
    private final PurchaseInviteService purchaseInviteService;

    public ExternalAuthApplicationService(
        AuthBoundaryRepository authBoundaryRepository,
        PurchaseInviteService purchaseInviteService
    ) {
        this.authBoundaryRepository = authBoundaryRepository;
        this.purchaseInviteService = purchaseInviteService;
    }

    public AppWechatLoginResponse appWechatLogin(AppWechatLoginCommand command) {
        String wxCode = command.wxCode() == null ? "" : command.wxCode().trim();
        if (!wxCode.startsWith("mock:") || wxCode.length() == "mock:".length()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "S3 仅支持 mock 小程序登录码");
        }
        String userNo = wxCode.substring("mock:".length());
        AppStudentRecord student = authBoundaryRepository.findActiveStudentByUserNo(userNo)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "学员身份不存在或已禁用"));
        return new AppWechatLoginResponse(
            student.userNo(),
            student.studentNo(),
            "S3-APP-DEMO-" + student.studentNo(),
            true);
    }

    public SupplierH5TokenResponse supplierH5Token(SupplierH5TokenCommand command) {
        String accessToken = command == null ? null : command.accessToken();
        // 1) 邀请链接路径：access_token = "invite:<token>"，自动绑定 supplier_no
        if (accessToken != null && accessToken.startsWith(INVITE_PREFIX)) {
            String token = accessToken.substring(INVITE_PREFIX.length()).trim();
            Optional<InviteEntry> entry = purchaseInviteService.resolveInvite(token);
            if (entry.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "邀请链接无效或已过期");
            }
            String inviteSupplierNo = entry.get().supplierNo();
            SupplierAuthRecord supplier = authBoundaryRepository.findActiveSupplierByNo(inviteSupplierNo)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "供货商不存在或未启用 H5 访问"));
            return new SupplierH5TokenResponse(
                supplier.supplierNo(),
                supplier.supplierName(),
                "S3-SUPPLIER-DEMO-" + supplier.supplierNo());
        }
        // 2) 兼容 mock 直登路径
        if (!"mock:SUPPLIER_S3".equals(accessToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "供货商访问令牌无效");
        }
        SupplierAuthRecord supplier = authBoundaryRepository.findActiveSupplierByNo(command.supplierNo())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "供货商不存在或未启用 H5 访问"));
        return new SupplierH5TokenResponse(
            supplier.supplierNo(),
            supplier.supplierName(),
            "S3-SUPPLIER-DEMO-" + supplier.supplierNo());
    }
}
