package com.wecombft.infrastructure.integration.mail;

public interface MailService {

    /**
     * 发送一封普通文本邮件。具体实现可能为 mock（仅写日志）或 real（JavaMailSender）。
     *
     * @param to 收件邮箱
     * @param subject 主题
     * @param content 正文（纯文本）
     * @return 发送结果
     */
    MailSendResult send(String to, String subject, String content);
}
