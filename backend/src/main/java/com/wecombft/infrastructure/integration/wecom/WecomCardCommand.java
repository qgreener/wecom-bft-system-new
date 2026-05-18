package com.wecombft.infrastructure.integration.wecom;

public record WecomCardCommand(
    String receiverWecomUserId,
    String title,
    String description,
    String url,
    String btnText
) {
}
