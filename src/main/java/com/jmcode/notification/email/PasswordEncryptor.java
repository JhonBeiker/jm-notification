package com.jmcode.notification.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordEncryptor {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public PasswordEncryptor(@Value("${notification.email.password-encryption-key:}") String configuredKey) {
        byte[] raw = decodeKey(configuredKey);
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                    "notification.email.password-encryption-key must decode to 16, 24 or 32 bytes (got " + raw.length + ")");
        }
        this.key = new SecretKeySpec(raw, ALGORITHM);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (plaintext.startsWith(PREFIX)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt password", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        String body = stored.substring(PREFIX.length());
        int sep = body.indexOf(':');
        if (sep <= 0) {
            throw new IllegalStateException("Malformed encrypted password payload");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(body.substring(0, sep));
            byte[] ciphertext = Base64.getDecoder().decode(body.substring(sep + 1));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt password", ex);
        }
    }

    private static byte[] decodeKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            return generated;
        }
        try {
            return Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException ex) {
            return configuredKey.getBytes(StandardCharsets.UTF_8);
        }
    }
}
