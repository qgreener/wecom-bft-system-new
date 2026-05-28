package com.wecombft.infrastructure.integration.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "integration", name = "message-mode", havingValue = "mock", matchIfMissing = true)
public class MockMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MockMailService.class);

    @Override
    public MailSendResult send(String to, String subject, String content) {
        if (to == null || to.isBlank()) {
            return MailSendResult.fail("收件邮箱为空");
        }
        log.info("[MOCK_MAIL] to={}, subject={}, content_len={}", to, subject, content == null ? 0 : content.length());
        return MailSendResult.ok("mock-mail-" + System.currentTimeMillis());
    }
}
