package com.jmcode.notification.email;

import com.jmcode.notification.config.NotificationProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncryptorTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private static PasswordEncryptor encryptorWithKey(String key) {
        NotificationProperties properties = new NotificationProperties(
                new NotificationProperties.Email(true, "", key),
                new NotificationProperties.WhatsApp(false, "", "", ""),
                new NotificationProperties.Telegram(false, "", "", true, "", "", "", false,
                        Duration.ofMinutes(2), Duration.ofSeconds(10)));
        return new PasswordEncryptor(properties);
    }

    @Test
    void encryptsAndDecryptsRoundTrip() {
        PasswordEncryptor encryptor = encryptorWithKey(KEY);

        String encrypted = encryptor.encrypt("super-secret");

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals("super-secret", encrypted);
        assertEquals("super-secret", encryptor.decrypt(encrypted));
    }

    @Test
    void reencryptingAnAlreadyEncryptedValueIsANoOp() {
        PasswordEncryptor encryptor = encryptorWithKey(KEY);
        String encrypted = encryptor.encrypt("secret");

        assertEquals(encrypted, encryptor.encrypt(encrypted));
    }

    @Test
    void usesRandomIvSoSamePlaintextGivesDifferentCiphertext() {
        PasswordEncryptor encryptor = encryptorWithKey(KEY);

        assertNotEquals(encryptor.encrypt("secret"), encryptor.encrypt("secret"));
    }

    @Test
    void plainLegacyValuesArePassedThroughUnchanged() {
        PasswordEncryptor encryptor = encryptorWithKey(KEY);

        assertEquals("legacy-plain", encryptor.decrypt("legacy-plain"));
        assertFalse(encryptor.isEncrypted("legacy-plain"));
    }

    @Test
    void decryptingWithADifferentKeyFails() {
        String other = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
        String encrypted = encryptorWithKey(KEY).encrypt("secret");

        assertThrows(IllegalStateException.class, () -> encryptorWithKey(other).decrypt(encrypted));
    }

    @Test
    void rejectsKeyWithInvalidLength() {
        String tooShort = Base64.getEncoder().encodeToString("short".getBytes());

        assertThrows(IllegalStateException.class, () -> encryptorWithKey(tooShort));
    }

    @Test
    void blankKeyFallsBackToARandomOneSoTheAppStillBoots() {
        PasswordEncryptor encryptor = encryptorWithKey("");

        assertEquals("secret", encryptor.decrypt(encryptor.encrypt("secret")));
    }
}
