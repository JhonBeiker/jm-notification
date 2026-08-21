package com.jmcode.notification.telegram;

import com.jmcode.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class TelegramWebhookRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramWebhookService webhookService;
    private final TelegramBotClient botClient;
    private final NotificationProperties.Telegram properties;

    public TelegramWebhookRegistrar(
            TelegramWebhookService webhookService,
            TelegramBotClient botClient,
            NotificationProperties properties
    ) {
        this.webhookService = webhookService;
        this.botClient = botClient;
        this.properties = properties.telegram();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || !properties.webhookAutoRegister()) {
            return;
        }
        if (!botClient.isConfigured()) {
            log.warn("Telegram webhook auto-register skipped: bot token not configured");
            return;
        }
        if (!StringUtils.hasText(properties.webhookUrl())) {
            log.warn("Telegram webhook auto-register skipped: TELEGRAM_WEBHOOK_URL is empty");
            return;
        }

        try {
            Map<String, Object> result = webhookService.register(null);
            log.info("Telegram webhook auto-registered: {}", result.get("webhookUrl"));
        } catch (Exception ex) {
            log.error("Telegram webhook auto-register failed: {}", ex.getMessage());
        }
    }
}
