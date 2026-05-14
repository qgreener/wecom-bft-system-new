package com.wecombft.interfaces.dto.iam;

import com.wecombft.infrastructure.persistence.iam.ApprovalRecord;
import java.util.List;

public record ApprovalPage(List<ApprovalRecord> records) {
}
