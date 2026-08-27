package com.citics.glxt.contractchange.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 计算工具，统一输出 64 位小写十六进制文本。 */
public final class HashUtils {
    private HashUtils() {
    }

    /** 计算 UTF-8 文本的 SHA-256。 */
    public static String sha256(String text) {
        return toHex(digest().digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            value.append(String.format("%02x", b & 0xff));
        }
        return value.toString();
    }
}
