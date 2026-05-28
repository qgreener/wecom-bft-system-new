package com.wecombft.infrastructure.integration.mail;

public record MailSendResult(boolean success, String messageId, String failureReason) {

    public static MailSendResult ok(String messageId) {
        return new MailSendResult(true, messageId, null);
    }

    public static MailSendResult fail(String reason) {
        return new MailSendResult(false, null, reason);
    }
}
