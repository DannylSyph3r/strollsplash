package dev.slethware.strollsplash.utility;

import dev.slethware.strollsplash.dto.response.SplashResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class ChecksumUtil {

    private ChecksumUtil() {}

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM must ship SHA-256, so this cannot happen
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // Covers everything the client sees, url included, so a CDN domain change also changes the ETag
    public static String etagFor(List<SplashResponse> items) {
        StringBuilder text = new StringBuilder();
        for (SplashResponse item : items) {
            text.append(item.getId()).append('|')
                    .append(item.getUrl()).append('|')
                    .append(item.getChecksum()).append('|')
                    .append(item.getSizeBytes()).append('\n');
        }
        return sha256Hex(text.toString().getBytes(StandardCharsets.UTF_8));
    }
}
