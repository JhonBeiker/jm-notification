package com.jmcode.notification.telegram.dto;

import com.jmcode.notification.telegram.TelegramBotAccount;

import java.time.Instant;

public record TelegramBotAccountResponseDto(
        Long id,
        String clientCode,
        String name,
        String apiUrl,
        String webhookUrl,
        boolean webhookAutoRegister,
        boolean requireActiveSubscriber,
        String welcomeMessage,
        String goodbyeMessage,
        boolean active,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
    public static TelegramBotAccountResponseDto from(TelegramBotAccount account) {
        return new TelegramBotAccountResponseDto(
                account.getId(),
                account.getClientCode(),
                account.getName(),
                account.getApiUrl(),
                account.getWebhookUrl(),
                account.isWebhookAutoRegister(),
                account.isRequireActiveSubscriber(),
                account.getWelcomeMessage(),
                account.getGoodbyeMessage(),
                account.isActive(),
                account.isDefault(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}