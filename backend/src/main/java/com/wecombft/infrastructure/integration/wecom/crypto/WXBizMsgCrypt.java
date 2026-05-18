package com.wecombft.infrastructure.integration.wecom.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 企微「接收消息」签名校验 + AES-256-CBC 解密工具。
 *
 * 算法严格按腾讯官方文档实现：
 *   key = Base64.decode(encodingAesKey + "=")  // 32 字节
 *   iv  = key[0:16]
 *   signature = SHA1( sort([token, timestamp, nonce, encryptedMsg]).join("") )
 *   解密后字节布局：[16B random][4B msgLen 大端][msgLen 字节 明文][剩余 receiveId]
 */
public final class WXBizMsgCrypt {

    private WXBizMsgCrypt() {
    }

    public static String verifyUrl(
        String token, String aesKey, String receiveId,
        String msgSignature, String timestamp, String nonce, String echoStr
    ) {
        ensureSignature(token, timestamp, nonce, echoStr, msgSignature);
        DecryptedMessage decrypted = decrypt(aesKey, echoStr);
        ensureReceiverMatches(receiveId, decrypted.receiveId());
        return decrypted.content();
    }

    public static String decryptMsg(
        String token, String aesKey, String receiveId,
        String msgSignature, String timestamp, String nonce, String encryptedMsg
    ) {
        ensureSignature(token, timestamp, nonce, encryptedMsg, msgSignature);
        DecryptedMessage decrypted = decrypt(aesKey, encryptedMsg);
        ensureReceiverMatches(receiveId, decrypted.receiveId());
        return decrypted.content();
    }

    /**
     * 对称的 encrypt 方法，仅用于本地测试构造 fixture（与企微算法等价：
     * [16B random][4B msgLen 大端][msgLen 明文][receiveId] + PKCS#7 + AES-256-CBC）。
     */
    public static String encrypt(String aesKey, String receiveId, String plain, String random16) {
        if (aesKey == null || aesKey.length() != 43) {
            throw new WXBizMsgCryptException("AES_KEY_INVALID", "EncodingAESKey 必须为 43 个字符");
        }
        if (random16 == null || random16.length() != 16) {
            throw new WXBizMsgCryptException("RANDOM_INVALID", "random 必须为 16 字节字符串");
        }
        byte[] key = Base64.getDecoder().decode(aesKey + "=");
        byte[] iv = Arrays.copyOfRange(key, 0, 16);
        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
        byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);
        byte[] randomBytes = random16.getBytes(StandardCharsets.UTF_8);

        int msgLen = plainBytes.length;
        int total = 16 + 4 + msgLen + receiveIdBytes.length;
        byte[] buf = new byte[total];
        System.arraycopy(randomBytes, 0, buf, 0, 16);
        buf[16] = (byte) ((msgLen >> 24) & 0xff);
        buf[17] = (byte) ((msgLen >> 16) & 0xff);
        buf[18] = (byte) ((msgLen >> 8) & 0xff);
        buf[19] = (byte) (msgLen & 0xff);
        System.arraycopy(plainBytes, 0, buf, 20, msgLen);
        System.arraycopy(receiveIdBytes, 0, buf, 20 + msgLen, receiveIdBytes.length);

        int padLen = 32 - (buf.length % 32);
        if (padLen == 0) padLen = 32;
        byte[] padded = new byte[buf.length + padLen];
        System.arraycopy(buf, 0, padded, 0, buf.length);
        for (int i = buf.length; i < padded.length; i++) {
            padded[i] = (byte) padLen;
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] cipherText = cipher.doFinal(padded);
            return Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
            throw new WXBizMsgCryptException("AES_ENCRYPT_FAILED", "AES 加密失败", e);
        }
    }

    public static String sha1Signature(String token, String timestamp, String nonce, String encryptedMsg) {
        List<String> parts = Arrays.asList(token, timestamp, nonce, encryptedMsg);
        parts.sort(String::compareTo);
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            joined.append(part);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(joined.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new WXBizMsgCryptException("SHA1_FAILED", "SHA1 计算失败", e);
        }
    }

    private static void ensureSignature(
        String token, String timestamp, String nonce, String encryptedMsg, String expectedSignature
    ) {
        String actual = sha1Signature(token, timestamp, nonce, encryptedMsg);
        if (!actual.equalsIgnoreCase(expectedSignature)) {
            throw new WXBizMsgCryptException("SIGNATURE_MISMATCH",
                "msg_signature 不匹配，expected=" + expectedSignature + " actual=" + actual);
        }
    }

    private static void ensureReceiverMatches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (!expected.equals(actual)) {
            throw new WXBizMsgCryptException("RECEIVER_MISMATCH",
                "解密后 receive_id (" + actual + ") 与配置的 corp_id (" + expected + ") 不一致");
        }
    }

    private static DecryptedMessage decrypt(String aesKey, String encryptedBase64) {
        if (aesKey == null || aesKey.length() != 43) {
            throw new WXBizMsgCryptException("AES_KEY_INVALID", "EncodingAESKey 必须为 43 个字符");
        }
        byte[] key = Base64.getDecoder().decode(aesKey + "=");
        if (key.length != 32) {
            throw new WXBizMsgCryptException("AES_KEY_INVALID", "AES key 解码长度不是 32 字节");
        }
        byte[] iv = Arrays.copyOfRange(key, 0, 16);
        byte[] cipherText = Base64.getDecoder().decode(encryptedBase64);

        byte[] plain;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            plain = cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new WXBizMsgCryptException("AES_DECRYPT_FAILED", "AES 解密失败", e);
        }
        byte[] unpadded = pkcs7Unpad(plain);
        if (unpadded.length < 20) {
            throw new WXBizMsgCryptException("PLAIN_TOO_SHORT", "解密后明文长度小于 20 字节");
        }
        int msgLen = ((unpadded[16] & 0xff) << 24)
            | ((unpadded[17] & 0xff) << 16)
            | ((unpadded[18] & 0xff) << 8)
            | (unpadded[19] & 0xff);
        if (msgLen < 0 || 20 + msgLen > unpadded.length) {
            throw new WXBizMsgCryptException("MSG_LEN_INVALID", "msgLen=" + msgLen + " 超出明文长度");
        }
        String content = new String(unpadded, 20, msgLen, StandardCharsets.UTF_8);
        String receiveId = new String(unpadded, 20 + msgLen, unpadded.length - 20 - msgLen, StandardCharsets.UTF_8);
        return new DecryptedMessage(content, receiveId);
    }

    private static byte[] pkcs7Unpad(byte[] padded) {
        int pad = padded[padded.length - 1] & 0xff;
        if (pad < 1 || pad > 32) {
            return padded;
        }
        return Arrays.copyOfRange(padded, 0, padded.length - pad);
    }

    public record DecryptedMessage(String content, String receiveId) {
    }

    public static class WXBizMsgCryptException extends RuntimeException {

        private final String errorCode;

        public WXBizMsgCryptException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public WXBizMsgCryptException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
