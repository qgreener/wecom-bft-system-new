package com.wecombft.infrastructure.integration.wecom;

public record WecomCardResult(
    boolean success,
    String wecomMsgId,
    String failureReason
) {

    public static WecomCardResult success(String wecomMsgId) {
        return new WecomCardResult(true, wecomMsgId, null);
    }

    public static WecomCardResult failure(String reason) {
        return new WecomCardResult(false, null, reason);
    }
}
