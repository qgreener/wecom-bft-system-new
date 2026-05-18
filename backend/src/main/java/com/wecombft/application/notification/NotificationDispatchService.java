package com.wecombft.application.notification;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.integration.wecom.WecomCardAdapter;
import com.wecombft.infrastructure.integration.wecom.WecomCardCommand;
import com.wecombft.infrastructure.integration.wecom.WecomCardResult;
import com.wecombft.infrastructure.persistence.iam.IamRepository;
import com.wecombft.infrastructure.persistence.notification.NotificationRepository;
import com.wecombft.infrastructure.persistence.notification.NotificationRepository.NotificationWriteCommand;
import com.wecombft.shared.id.IdGenerator;

/**
 * 统一通知出口：
 * - 内部员工：同时写 IN_APP + WECOM_CARD 两条 notify_message；WECOM_CARD 同步推送企微，失败仅记录 send_status=FAILED
 * - 学员：只写 IN_APP 一条（学员无 wecom_user_id，由小程序通知页拉取展示）
 *
 * 设计参考 doc 07 §4.2「企微卡片发送失败只记录失败原因，不回滚主业务」
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationRepository notificationRepository;
    private final IamRepository iamRepository;
    private final WecomCardAdapter wecomCardAdapter;
    private final IdGenerator idGenerator;

    public NotificationDispatchService(
        NotificationRepository notificationRepository,
        IamRepository iamRepository,
        WecomCardAdapter wecomCardAdapter,
        IdGenerator idGenerator
    ) {
        this.notificationRepository = notificationRepository;
        this.iamRepository = iamRepository;
        this.wecomCardAdapter = wecomCardAdapter;
        this.idGenerator = idGenerator;
    }

    public void dispatchToInternalUser(Long receiverUserId, NotificationContent content) {
        if (receiverUserId == null || content == null) {
            return;
        }
        writeInternalChannel(receiverUserId, "IN_APP", content);
        Long wecomNotificationId = writeInternalChannel(receiverUserId, "WECOM_CARD", content);
        if (wecomNotificationId != null) {
            pushWecomCard(receiverUserId, wecomNotificationId, content);
        }
    }

    public void dispatchToStudent(Long receiverStudentId, NotificationContent content) {
        if (receiverStudentId == null || content == null) {
            return;
        }
        long notificationId = idGenerator.nextId();
        notificationRepository.insertIfAbsent(NotificationWriteCommand.forStudent(
            notificationId,
            "NTF" + notificationId,
            receiverStudentId,
            "IN_APP",
            content.sceneCode(),
            content.templateCode(),
            content.title(),
            content.content(),
            content.relatedObjectType(),
            content.relatedObjectId(),
            content.idempotencyKey() + ":STUDENT:IN_APP",
            content.createdBy()));
    }

    private Long writeInternalChannel(Long receiverUserId, String channel, NotificationContent content) {
        long notificationId = idGenerator.nextId();
        return notificationRepository.insertIfAbsent(NotificationWriteCommand.forInternalUser(
            notificationId,
            "NTF" + notificationId,
            receiverUserId,
            channel,
            content.sceneCode(),
            content.templateCode(),
            content.title(),
            content.content(),
            content.relatedObjectType(),
            content.relatedObjectId(),
            content.idempotencyKey() + ":" + channel,
            content.createdBy()));
    }

    private void pushWecomCard(Long receiverUserId, Long notificationId, NotificationContent content) {
        Optional<String> wecomUserId = iamRepository.findWecomUserIdByUserId(receiverUserId);
        if (wecomUserId.isEmpty()) {
            notificationRepository.updateSendStatus(notificationId, "FAILED", LocalDateTime.now(),
                "RECEIVER_HAS_NO_WECOM_BINDING");
            return;
        }
        WecomCardResult result;
        try {
            result = wecomCardAdapter.sendCard(new WecomCardCommand(
                wecomUserId.get(),
                content.title(),
                content.content(),
                content.clickUrl(),
                "查看详情"));
        } catch (RuntimeException e) {
            log.warn("dispatchWecomCard adapter threw: {}", e.getMessage());
            notificationRepository.updateSendStatus(notificationId, "FAILED", LocalDateTime.now(),
                "ADAPTER_EXCEPTION:" + e.getMessage());
            return;
        }
        if (result == null) {
            notificationRepository.updateSendStatus(notificationId, "FAILED", LocalDateTime.now(),
                "ADAPTER_NULL_RESULT");
            return;
        }
        if (result.success()) {
            notificationRepository.updateSendStatus(notificationId, "SENT", LocalDateTime.now(), null);
        } else {
            notificationRepository.updateSendStatus(notificationId, "FAILED", LocalDateTime.now(),
                result.failureReason());
        }
    }
}
