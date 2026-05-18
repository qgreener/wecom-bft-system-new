package com.wecombft.application.iam;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.integration.wecom.WecomAuthAdapter;
import com.wecombft.infrastructure.integration.wecom.WecomAuthAdapterException;
import com.wecombft.infrastructure.integration.wecom.WecomUserInfo;
import com.wecombft.infrastructure.persistence.iam.IamRepository;
import com.wecombft.infrastructure.persistence.iam.UserRecord;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminSessionService;
import com.wecombft.infrastructure.security.PermissionCatalog.DataScopePolicy;
import com.wecombft.infrastructure.security.PermissionCatalog.FieldMaskPolicy;
import com.wecombft.infrastructure.security.PermissionCatalog.MenuPolicy;
import com.wecombft.shared.web.ApiException;

import com.wecombft.interfaces.dto.iam.CurrentUserResponse;
import com.wecombft.interfaces.dto.iam.DataScopeView;
import com.wecombft.interfaces.dto.iam.FieldMaskView;
import com.wecombft.interfaces.dto.iam.MenuView;
import com.wecombft.interfaces.dto.iam.RoleView;
import com.wecombft.interfaces.dto.iam.TestLoginResponse;
@Service
public class AdminAuthApplicationService {

    private final AdminSessionService adminSessionService;
    private final WecomAuthAdapter wecomAuthAdapter;
    private final IamRepository iamRepository;

    public AdminAuthApplicationService(
        AdminSessionService adminSessionService,
        WecomAuthAdapter wecomAuthAdapter,
        IamRepository iamRepository
    ) {
        this.adminSessionService = adminSessionService;
        this.wecomAuthAdapter = wecomAuthAdapter;
        this.iamRepository = iamRepository;
    }

    public TestLoginResponse testLogin(String userNo) {
        return new TestLoginResponse(
            userNo,
            adminSessionService.issueTestToken(userNo),
            7200,
            "S3_DEMO_TOKEN",
            OffsetDateTime.now().plusSeconds(7200)
        );
    }

    public TestLoginResponse wecomLogin(String authCode) {
        WecomUserInfo info;
        try {
            info = wecomAuthAdapter.exchangeCode(authCode);
        } catch (WecomAuthAdapterException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, e.errorCode(), e.getMessage());
        }
        UserRecord user = iamRepository.findActiveUserByWecomUserId(info.wecomUserId())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "WECOM_USER_NOT_BOUND",
                "企微账号未绑定系统用户：wecom_user_id=" + info.wecomUserId()));
        return testLogin(user.userNo());
    }

    public String buildWecomOAuthStartUrl(String state, String redirectUri) {
        return wecomAuthAdapter.buildOAuthUrl(state, redirectUri).url();
    }

    public CurrentUserResponse currentUser(String authorizationHeader) {
        AdminPrincipal principal = adminSessionService.require(authorizationHeader);
        return fromPrincipal(principal);
    }

    public CurrentUserResponse fromPrincipal(AdminPrincipal principal) {
        return new CurrentUserResponse(
            principal.userId(),
            principal.userNo(),
            principal.displayName(),
            principal.roles().stream()
                .map(role -> new RoleView(role.roleCode(), role.roleName(), role.dataScope()))
                .toList(),
            principal.permissionView().permissionCodes(),
            toDataScope(principal.permissionView().dataScope()),
            principal.permissionView().menus().stream().map(this::toMenu).toList(),
            principal.permissionView().fieldMasks().stream().map(this::toMask).toList()
        );
    }

    private DataScopeView toDataScope(DataScopePolicy policy) {
        return new DataScopeView(policy.scopeCode(), policy.description());
    }

    private MenuView toMenu(MenuPolicy policy) {
        return new MenuView(policy.menuCode(), policy.menuName(), policy.parentCode(), policy.sortNo());
    }

    private FieldMaskView toMask(FieldMaskPolicy policy) {
        return new FieldMaskView(policy.fieldCode(), policy.displayName(), policy.maskStrategy());
    }






}
