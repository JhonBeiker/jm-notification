package com.jmcode.notification.telegram;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Endpoint público que recibe los updates de Telegram.
 *
 * <p>La cuenta se identifica por el header {@code X-Telegram-Bot-Api-Secret-Token}. Si
 * alguna cuenta activa tiene secret configurado, un secret ausente o desconocido se
 * rechaza con 401; antes se caía siempre a la cuenta por defecto, con lo que cualquiera
 * podía inyectar updates falsos.
 */
@RestController
@RequestMapping("/api/v1/telegram")
@Hidden
@RequiredArgsConstructor
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramSubscriberService subscriberService;
    private final TelegramBotAccountService accountService;

    @GetMapping("/webhook")
    public Map<String, Object> webhookHealth() {
        return Map.of(
                "status", "up",
                "method", "POST required for Telegram updates",
                "path", "/api/v1/telegram/webhook"
        );
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody(required = false) TelegramUpdatePayload update
    ) {
        Optional<TelegramBotAccount> account = resolveAccount(secret);
        if (account.isEmpty()) {
            log.warn("Rejected Telegram webhook call with missing or unknown secret token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "unauthorized"));
        }

        if (update != null) {
            subscriberService.handleUpdate(update, account.get());
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private Optional<TelegramBotAccount> resolveAccount(String secret) {
        Optional<TelegramBotAccount> bySecret = accountService.findByWebhookSecret(secret);
        if (bySecret.isPresent()) {
            return bySecret;
        }
        if (accountService.webhookSecretRequired()) {
            return Optional.empty();
        }
        // Compatibilidad: ninguna cuenta define secret todavía, se usa la cuenta por defecto.
        try {
            return Optional.of(accountService.resolveAccount(null));
        } catch (RuntimeException ex) {
            log.warn("Telegram webhook received but no active bot account is configured: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
