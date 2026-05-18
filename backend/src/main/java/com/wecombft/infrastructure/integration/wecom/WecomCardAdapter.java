package com.wecombft.infrastructure.integration.wecom;

public interface WecomCardAdapter {

    WecomCardResult sendCard(WecomCardCommand command);
}
