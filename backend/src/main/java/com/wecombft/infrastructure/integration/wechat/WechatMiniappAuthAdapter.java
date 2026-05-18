package com.wecombft.infrastructure.integration.wechat;

public interface WechatMiniappAuthAdapter {

    WechatMiniappSession code2Session(String code);
}
