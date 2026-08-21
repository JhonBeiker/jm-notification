package com.jmcode.notification.web;

import com.jmcode.notification.config.NotificationProperties;
import com.jmcode.notification.telegram.TelegramSubscriberService;
import com.jmcode.notification.telegram.TelegramUpdatePayload;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/telegram")
@Hidden
public class TelegramWebhookController {

    private final TelegramSubscriberService subscriberService;
    private final NotificationProperties.Telegram properties;

    public TelegramWebhookController(TelegramSubscriberService subscriberService, NotificationProperties properties) {
        this.subscriberService = subscriberService;
        this.properties = properties.telegram();
    }

    @GetMapping("/webhook")
    public Map<String, Object> webhookHealth() {
        return Map.of(
                "status", "up",
                "method", "POST required for Telegram updates",
                "path", "/api/v1/telegram/webhook",
                "secretConfigured", StringUtils.hasText(properties.webhookSecret())
        );
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody(required = false) TelegramUpdatePayload update
    ) {
        if (StringUtils.hasText(properties.webhookSecret())
                && !properties.webhookSecret().equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "unauthorized"));
        }

        if (update != null) {
            subscriberService.handleUpdate(update);
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
