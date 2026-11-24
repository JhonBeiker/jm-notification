package com.jmcode.notification.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void generatesPrefixedKeys() {
        ApiKeyGenerator.GeneratedApiKey key = generator.generate();

        assertTrue(key.plainKey().startsWith("jmk_"));
        assertEquals(key.plainKey().substring(0, 12), key.prefix());
    }

    @Test
    void generatesADifferentKeyEveryTime() {
        assertNotEquals(generator.generate().plainKey(), generator.generate().plainKey());
    }

    @Test
    void hashIsStableAndMatchesTheGeneratedOne() {
        ApiKeyGenerator.GeneratedApiKey key = generator.generate();

        assertEquals(key.hash(), generator.hash(key.plainKey()));
        assertEquals(64, key.hash().length(), "SHA-256 en hexadecimal");
    }

    @Test
    void hashDiffersForDifferentKeys() {
        assertNotEquals(generator.hash("jmk_a"), generator.hash("jmk_b"));
    }

    @Test
    void hashNeverContainsThePlainKey() {
        ApiKeyGenerator.GeneratedApiKey key = generator.generate();

        assertTrue(key.hash().matches("[0-9a-f]{64}"));
        assertNotEquals(key.plainKey(), key.hash());
    }
}
