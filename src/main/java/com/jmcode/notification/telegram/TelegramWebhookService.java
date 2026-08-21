package com.jmcode.notification.telegram;

import com.jmcode.notification.config.NotificationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TelegramWebhookService {

    private static final String WEBHOOK_PATH = "/api/v1/telegram/webhook";

    private final TelegramBotClient botClient;
    private final NotificationProperties.Telegram properties;

    public TelegramWebhookService(TelegramBotClient botClient, NotificationProperties properties) {
        this.botClient = botClient;
        this.properties = properties.telegram();
    }

    public Map<String, Object> register(String publicBaseUrl) {
        String webhookUrl = resolveWebhookUrl(publicBaseUrl);
        Map<String, Object> telegramResponse = botClient.setWebhook(webhookUrl, properties.webhookSecret());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("webhookUrl", webhookUrl);
        result.put("secretConfigured", StringUtils.hasText(properties.webhookSecret()));
        result.put("telegram", telegramResponse);
        return result;
    }

    public Map<String, Object> unregister() {
        Map<String, Object> telegramResponse = botClient.deleteWebhook();
        return Map.of("telegram", telegramResponse);
    }

    public Map<String, Object> info() {
        Map<String, Object> telegramResponse = botClient.getWebhookInfo();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configuredWebhookUrl", properties.webhookUrl());
        result.put("autoRegister", properties.webhookAutoRegister());
        result.put("secretConfigured", StringUtils.hasText(properties.webhookSecret()));
        result.put("telegram", telegramResponse);
        return result;
    }

    public Map<String, Object> botInfo() {
        return botClient.getMe();
    }

    private String resolveWebhookUrl(String publicBaseUrl) {
        if (StringUtils.hasText(publicBaseUrl)) {
            return join(publicBaseUrl.trim(), WEBHOOK_PATH);
        }
        if (StringUtils.hasText(properties.webhookUrl())) {
            String configured = properties.webhookUrl().trim();
            if (configured.endsWith(WEBHOOK_PATH)) {
                return configured;
            }
            return join(configured, WEBHOOK_PATH);
        }
        throw new IllegalArgumentException(
                "Webhook URL required. Pass publicBaseUrl in the request body or set TELEGRAM_WEBHOOK_URL"
        );
    }

    private static String join(String base, String path) {
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalized + path;
    }
}
