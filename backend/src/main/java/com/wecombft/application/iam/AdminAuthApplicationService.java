package com.wecombft.application.iam;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminSessionService;
import com.wecombft.infrastructure.security.PermissionCatalog.DataScopePolicy;
import com.wecombft.infrastructure.security.PermissionCatalog.FieldMaskPolicy;
import com.wecombft.infrastructure.security.PermissionCatalog.MenuPolicy;

@Service
public class AdminAuthApplicationService {

    private final AdminSessionService adminSessionService;

    public AdminAuthApplicationService(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
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

    public record TestLoginResponse(
        String userNo,
        String accessToken,
        int expiresIn,
        String tokenType,
        OffsetDateTime expireAt
    ) {
    }

    public record CurrentUserResponse(
        long userId,
        String userNo,
        String displayName,
        List<RoleView> roles,
        List<String> permissionCodes,
        DataScopeView dataScope,
        List<MenuView> menus,
        List<FieldMaskView> fieldMasks
    ) {
    }

    public record RoleView(String roleCode, String roleName, String dataScope) {
    }

    public record DataScopeView(String scopeCode, String description) {
    }

    public record MenuView(String menuCode, String menuName, String parentCode, int sortNo) {
    }

    public record FieldMaskView(String fieldCode, String displayName, String maskStrategy) {
    }
}
