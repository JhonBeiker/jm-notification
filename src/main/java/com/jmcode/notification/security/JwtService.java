package com.jmcode.notification.security;

import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    /**
     * Mapper propio, deliberadamente no expuesto como bean: declarar un {@code ObjectMapper}
     * en el contexto desactivaba el de Spring Boot (es {@code @ConditionalOnMissingBean}) y
     * dejaba a todo el MVC sin los módulos que Boot registra.
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final byte[] secret;
    private final long ttlMillis;

    public JwtService(AuthProperties props) {
        this.secret = resolveSecret(props.jwtSecret());
        this.ttlMillis = props.jwtTtl().toMillis();
    }

    public String issue(Long userId, String email, Role role, Long companyId) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", String.valueOf(userId));
        claims.put("email", email);
        claims.put("role", role.name());
        // Ámbito de empresa: ausente para SUPER_ADMIN, que no está limitado a ninguna.
        if (companyId != null) {
            claims.put("cid", companyId);
        }
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusMillis(ttlMillis).getEpochSecond());

        try {
            String h = B64.encodeToString(MAPPER.writeValueAsBytes(header));
            String p = B64.encodeToString(MAPPER.writeValueAsBytes(claims));
            String signingInput = h + "." + p;
            String s = B64.encodeToString(hmac(signingInput));
            return signingInput + "." + s;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to issue JWT", ex);
        }
    }

    public Optional<AuthPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String signingInput = parts[0] + "." + parts[1];
        try {
            byte[] expected = hmac(signingInput);
            byte[] provided = B64D.decode(parts[2]);
            if (!constantTimeEquals(expected, provided)) {
                return Optional.empty();
            }
            Map<?, ?> claims = MAPPER.readValue(B64D.decode(parts[1]), Map.class);
            Object exp = claims.get("exp");
            if (exp instanceof Number n && Instant.now().getEpochSecond() >= n.longValue()) {
                return Optional.empty();
            }
            Object sub = claims.get("sub");
            Object email = claims.get("email");
            Object role = claims.get("role");
            if (!(sub instanceof String s) || !(email instanceof String e) || !(role instanceof String r)) {
                return Optional.empty();
            }
            Long companyId = claims.get("cid") instanceof Number cid ? cid.longValue() : null;
            return Optional.of(new AuthPrincipal(Long.parseLong(s), e, Role.valueOf(r), companyId));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to compute HMAC", ex);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static byte[] resolveSecret(String configured) {
        if (configured != null && !configured.isBlank()) {
            byte[] raw = configured.getBytes(StandardCharsets.UTF_8);
            if (raw.length < MIN_SECRET_BYTES) {
                // Antes se rellenaba con ceros hasta 32 bytes, debilitando la clave en silencio.
                throw new IllegalStateException(
                        "notification.auth.jwt-secret must be at least " + MIN_SECRET_BYTES
                                + " characters (got " + raw.length + "). Generate one with: openssl rand -base64 32");
            }
            return raw;
        }
        log.warn("JWT_SECRET is not set: generating a random signing key for this run. "
                + "All issued tokens become invalid on restart.");
        byte[] generated = new byte[MIN_SECRET_BYTES];
        new SecureRandom().nextBytes(generated);
        return generated;
    }
}
