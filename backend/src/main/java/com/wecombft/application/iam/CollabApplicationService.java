package com.wecombft.application.iam;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.persistence.iam.ApprovalRecord;
import com.wecombft.infrastructure.persistence.iam.IamRepository;
import com.wecombft.infrastructure.persistence.notification.NotificationRecord;
import com.wecombft.infrastructure.persistence.notification.NotificationRepository;
import com.wecombft.infrastructure.persistence.notification.NotificationRepository.NotificationQuery;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminSessionService;

import com.wecombft.interfaces.dto.iam.ApprovalPage;
import com.wecombft.interfaces.dto.iam.NotificationPage;
@Service
public class CollabApplicationService {

    private final AdminSessionService adminSessionService;
    private final IamRepository iamRepository;
    private final NotificationRepository notificationRepository;

    public CollabApplicationService(
        AdminSessionService adminSessionService,
        IamRepository iamRepository,
        NotificationRepository notificationRepository
    ) {
        this.adminSessionService = adminSessionService;
        this.iamRepository = iamRepository;
        this.notificationRepository = notificationRepository;
    }

    public ApprovalPage approvals(
        String authorizationHeader,
        String role,
        String approvalType,
        String status
    ) {
        adminSessionService.requirePermission(authorizationHeader, "iam:role-application:approve");
        return new ApprovalPage(iamRepository.findApprovals(approvalType, status));
    }

    public NotificationPage notifications(String authorizationHeader, String sceneCode) {
        AdminPrincipal principal = adminSessionService.require(authorizationHeader);
        Long receiverUserId = principal.permissionView().permissionCodes().contains("iam:role-application:approve")
            ? null
            : principal.userId();
        return new NotificationPage(notificationRepository.search(new NotificationQuery(sceneCode, receiverUserId)));
    }


}
