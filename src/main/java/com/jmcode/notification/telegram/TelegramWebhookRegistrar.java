package com.jmcode.notification.telegram;

import com.jmcode.notification.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Registra en Telegram el webhook de cada cuenta activa marcada con
 * {@code webhookAutoRegister}. Un fallo aquí no impide arrancar: se registra y se sigue.
 */
@Component
@RequiredArgsConstructor
public class TelegramWebhookRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramWebhookService webhookService;
    private final TelegramBotAccountService accountService;
    private final NotificationProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.telegram().enabled()) {
            return;
        }
        accountService.listActive().stream()
                .filter(TelegramBotAccount::isWebhookAutoRegister)
                .forEach(this::registerQuietly);
    }

    private void registerQuietly(TelegramBotAccount account) {
        if (!StringUtils.hasText(account.getWebhookUrl())) {
            log.warn("Telegram webhook auto-register skipped for {}: webhookUrl is empty", account.getClientCode());
            return;
        }
        try {
            Map<String, Object> result = webhookService.register(null, account.getClientCode());
            log.info("Telegram webhook auto-registered for {}: {}", account.getClientCode(), result.get("webhookUrl"));
        } catch (Exception ex) {
            log.error("Telegram webhook auto-register failed for {}", account.getClientCode(), ex);
        }
    }
}
