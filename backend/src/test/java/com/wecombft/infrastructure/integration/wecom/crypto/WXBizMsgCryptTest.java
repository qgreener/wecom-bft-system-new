package com.wecombft.infrastructure.integration.wecom.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WXBizMsgCryptTest {

    private static final String TOKEN = "TestToken123";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLM1234";
    private static final String CORP_ID = "wxTestCorpId12345";
    private static final String RANDOM_16 = "1234567890abcdef";

    @Test
    void verifyUrl_roundTrip_returnsPlainText() {
        String plain = "1616140317555161061";
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, plain, RANDOM_16);
        String timestamp = "1409659589";
        String nonce = "263014780";
        String signature = WXBizMsgCrypt.sha1Signature(TOKEN, timestamp, nonce, encrypted);

        String result = WXBizMsgCrypt.verifyUrl(TOKEN, AES_KEY, CORP_ID,
            signature, timestamp, nonce, encrypted);

        assertThat(result).isEqualTo(plain);
    }

    @Test
    void decryptMsg_roundTrip_returnsXmlBody() {
        String xml = "<xml><MsgType><![CDATA[event]]></MsgType><Event><![CDATA[sys_approval_change]]></Event>"
            + "<ApprovalInfo><SpNo><![CDATA[201909270001]]></SpNo>"
            + "<SpStatus>2</SpStatus></ApprovalInfo></xml>";
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, xml, RANDOM_16);
        String timestamp = "1700000000";
        String nonce = "ABC123";
        String signature = WXBizMsgCrypt.sha1Signature(TOKEN, timestamp, nonce, encrypted);

        String result = WXBizMsgCrypt.decryptMsg(TOKEN, AES_KEY, CORP_ID,
            signature, timestamp, nonce, encrypted);

        assertThat(result).isEqualTo(xml);
    }

    @Test
    void verifyUrl_signatureMismatch_throws() {
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, "hello", RANDOM_16);

        assertThatThrownBy(() -> WXBizMsgCrypt.verifyUrl(TOKEN, AES_KEY, CORP_ID,
            "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", "1700000000", "nonce", encrypted))
            .isInstanceOf(WXBizMsgCrypt.WXBizMsgCryptException.class)
            .hasMessageContaining("msg_signature");
    }

    @Test
    void verifyUrl_wrongCorpId_throws() {
        String plain = "hello";
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, plain, RANDOM_16);
        String timestamp = "1700000000";
        String nonce = "nonce";
        String signature = WXBizMsgCrypt.sha1Signature(TOKEN, timestamp, nonce, encrypted);

        assertThatThrownBy(() -> WXBizMsgCrypt.verifyUrl(TOKEN, AES_KEY, "wxWrongCorp",
            signature, timestamp, nonce, encrypted))
            .isInstanceOf(WXBizMsgCrypt.WXBizMsgCryptException.class)
            .hasMessageContaining("receive_id");
    }

    @Test
    void sha1Signature_sortsThenJoins() {
        String sig = WXBizMsgCrypt.sha1Signature("abc", "1000", "xyz", "msg");
        assertThat(sig).hasSize(40).matches("[0-9a-f]+");
    }

    @Test
    void encrypt_with43CharKey_returnsBase64Ciphertext() {
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, "hello world", RANDOM_16);

        assertThat(encrypted).isNotBlank().matches("[A-Za-z0-9+/=]+");
    }

    @Test
    void encrypt_invalidAesKeyLength_throws() {
        assertThatThrownBy(() -> WXBizMsgCrypt.encrypt("short", CORP_ID, "hello", RANDOM_16))
            .isInstanceOf(WXBizMsgCrypt.WXBizMsgCryptException.class);
    }

    @Test
    void verifyUrl_invalidAesKeyLength_throws() {
        assertThatThrownBy(() -> WXBizMsgCrypt.verifyUrl(TOKEN, "short", CORP_ID,
            "deadbeef", "1700000000", "nonce", "anyEncryptedText"))
            .isInstanceOf(WXBizMsgCrypt.WXBizMsgCryptException.class);
    }
}
