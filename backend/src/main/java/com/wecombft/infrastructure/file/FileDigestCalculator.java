package com.wecombft.infrastructure.file;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class FileDigestCalculator {

    private static final int BUFFER_SIZE = 8192;

    private FileDigestCalculator() {
    }

    public static String sha256Hex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        MessageDigest digest = sha256();
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    public static String sha256Hex(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
