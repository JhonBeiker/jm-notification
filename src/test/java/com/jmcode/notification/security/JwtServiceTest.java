package com.jmcode.notification.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-32";

    private static JwtService jwtService(String secret, Duration ttl) {
        return new JwtService(new AuthProperties("", "", secret, ttl));
    }

    @Test
    void issuesATokenThatParsesBackToThePrincipal() {
        JwtService service = jwtService(SECRET, Duration.ofHours(12));

        String token = service.issue(7L, "admin@jmcode.local", Role.SUPER_ADMIN);
        Optional<AuthPrincipal> principal = service.parse(token);

        assertTrue(principal.isPresent());
        assertEquals(7L, principal.get().userId());
        assertEquals("admin@jmcode.local", principal.get().email());
        assertEquals(Role.SUPER_ADMIN, principal.get().role());
    }

    @Test
    void rejectsExpiredTokens() {
        JwtService service = jwtService(SECRET, Duration.ofSeconds(-60));

        String token = service.issue(1L, "admin@jmcode.local", Role.ADMIN);

        assertTrue(service.parse(token).isEmpty());
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String token = jwtService(SECRET, Duration.ofHours(1)).issue(1L, "admin@jmcode.local", Role.ADMIN);
        JwtService otherService = jwtService("a-different-secret-also-long-enough", Duration.ofHours(1));

        assertTrue(otherService.parse(token).isEmpty());
    }

    @Test
    void rejectsTamperedPayload() {
        JwtService service = jwtService(SECRET, Duration.ofHours(1));
        String token = service.issue(1L, "admin@jmcode.local", Role.ADMIN);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

        assertTrue(service.parse(tampered).isEmpty());
    }

    @Test
    void rejectsMalformedTokens() {
        JwtService service = jwtService(SECRET, Duration.ofHours(1));

        assertTrue(service.parse(null).isEmpty());
        assertTrue(service.parse("").isEmpty());
        assertTrue(service.parse("not-a-jwt").isEmpty());
    }

    @Test
    void refusesToStartWithATooShortSecretInsteadOfPaddingIt() {
        assertThrows(IllegalStateException.class, () -> jwtService("too-short", Duration.ofHours(1)));
    }

    @Test
    void generatesAnEphemeralSecretWhenNoneIsConfigured() {
        JwtService service = jwtService("", Duration.ofHours(1));

        String token = service.issue(1L, "admin@jmcode.local", Role.ADMIN);

        assertTrue(service.parse(token).isPresent());
    }
}
