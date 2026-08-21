package com.jmcode.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        Email email,
        WhatsApp whatsapp,
        Telegram telegram
) {
    public record Email(boolean enabled, String from) {
    }

    public record WhatsApp(boolean enabled, String apiUrl, String phoneNumberId, String accessToken) {
    }

    public record Telegram(
            boolean enabled,
            String botToken,
            String apiUrl,
            String webhookSecret,
            boolean requireActiveSubscriber,
            String welcomeMessage,
            String goodbyeMessage,
            String webhookUrl,
            boolean webhookAutoRegister
    ) {
    }
}
