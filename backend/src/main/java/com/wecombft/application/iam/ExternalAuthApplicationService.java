package com.wecombft.application.iam;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.persistence.iam.AuthBoundaryRepository;
import com.wecombft.infrastructure.persistence.iam.AuthBoundaryRepository.AppStudentRecord;
import com.wecombft.infrastructure.persistence.iam.AuthBoundaryRepository.SupplierAuthRecord;
import com.wecombft.shared.web.ApiException;

@Service
public class ExternalAuthApplicationService {

    private final AuthBoundaryRepository authBoundaryRepository;

    public ExternalAuthApplicationService(AuthBoundaryRepository authBoundaryRepository) {
        this.authBoundaryRepository = authBoundaryRepository;
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
        if (!"mock:SUPPLIER_S3".equals(command.accessToken())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "供货商访问令牌无效");
        }
        SupplierAuthRecord supplier = authBoundaryRepository.findActiveSupplierByNo(command.supplierNo())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "供货商不存在或未启用 H5 访问"));
        return new SupplierH5TokenResponse(
            supplier.supplierNo(),
            supplier.supplierName(),
            "S3-SUPPLIER-DEMO-" + supplier.supplierNo());
    }

    public record AppWechatLoginCommand(String wxCode, String sourceChannel) {
    }

    public record AppWechatLoginResponse(
        String userNo,
        String studentNo,
        String accessToken,
        boolean mobileBound
    ) {
    }

    public record SupplierH5TokenCommand(String accessToken, String supplierNo) {
    }

    public record SupplierH5TokenResponse(
        String supplierNo,
        String supplierName,
        String sessionToken
    ) {
    }
}
