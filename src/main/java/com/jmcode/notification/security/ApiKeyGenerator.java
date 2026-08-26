package com.jmcode.notification.security;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyGenerator {

    private static final String PREFIX = "jmk_";
    private static final int RANDOM_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public GeneratedApiKey generate() {
        byte[] raw = new byte[RANDOM_BYTES];
        random.nextBytes(raw);
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        return new GeneratedApiKey(token, token.substring(0, 12), sha256(token));
    }

    public String hash(String token) {
        return sha256(token);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record GeneratedApiKey(String plainKey, String prefix, String hash) {
    }
}
