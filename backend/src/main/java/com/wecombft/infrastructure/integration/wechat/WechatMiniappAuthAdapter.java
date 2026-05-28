package com.wecombft.infrastructure.integration.wechat;

public interface WechatMiniappAuthAdapter {

    WechatMiniappSession code2Session(String code);

    /**
     * 用 wx.getPhoneNumber 返回的 phoneCode 换取手机号。
     * Mock 模式下接受 "mock:13xxxxxxxxx" 形式的伪 phoneCode；
     * Real 模式下调用 /wxa/business/getuserphonenumber。
     */
    String getPhoneNumber(String phoneCode);
}
