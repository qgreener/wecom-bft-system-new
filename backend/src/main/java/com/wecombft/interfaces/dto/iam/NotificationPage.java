package com.wecombft.interfaces.dto.iam;

import java.util.List;
import com.wecombft.infrastructure.persistence.notification.NotificationRecord;

public record NotificationPage(List<NotificationRecord> records) {
}
