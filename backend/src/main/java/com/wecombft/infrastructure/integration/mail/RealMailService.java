package com.wecombft.infrastructure.integration.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "integration", name = "message-mode", havingValue = "real")
public class RealMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(RealMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public RealMailService(JavaMailSender mailSender, @Value("${spring.mail.from:no-reply@example.com}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public MailSendResult send(String to, String subject, String content) {
        if (to == null || to.isBlank()) {
            return MailSendResult.fail("收件邮箱为空");
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(content);
            mailSender.send(msg);
            return MailSendResult.ok("real-" + System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("[REAL_MAIL] send failed: to={}, subject={}, err={}", to, subject, e.getMessage());
            return MailSendResult.fail(e.getMessage());
        }
    }
}
