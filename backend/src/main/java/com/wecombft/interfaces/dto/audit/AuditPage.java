package com.wecombft.interfaces.dto.audit;

import com.wecombft.infrastructure.persistence.audit.AuditLogRecord;
import java.util.List;

public record AuditPage(List<AuditLogRecord> records, int pageNo, int pageSize, int total) {
}
