package com.wecombft.infrastructure.integration.wecom;

public interface WecomApprovalAdapter {

    WecomApprovalResult createApproval(WecomApprovalCommand command);
}
