package com.jmcode.notification.telegram.dto;

import com.jmcode.notification.telegram.TelegramBotAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TelegramBotAccountRequestDto(

        @NotBlank @Size(max = 64) String clientCode,

        @Size(max = 128) String name,

        @NotBlank
        @Pattern(regexp = "\\d+:[A-Za-z0-9_-]+", message = "botToken must look like '<botId>:<secret>' (from @BotFather)")
        String botToken,

        @Size(max = 255) String apiUrl,

        @Pattern(regexp = "[A-Za-z0-9_-]{1,256}|", message = "webhookSecret only allows A-Z a-z 0-9 _ - (max 256)")
        String webhookSecret,

        @Size(max = 512) String webhookUrl,

        boolean webhookAutoRegister,
        boolean requireActiveSubscriber,

        @Size(max = 512) String welcomeMessage,
        @Size(max = 512) String goodbyeMessage,

        boolean active,
        boolean isDefault
) {

    private static final String DEFAULT_API_URL = "https://api.telegram.org";

    public TelegramBotAccount applyTo(TelegramBotAccount entity) {
        entity.setClientCode(clientCode.trim());
        entity.setName(name);
        entity.setBotToken(botToken.trim());
        entity.setApiUrl(apiUrl == null || apiUrl.isBlank() ? DEFAULT_API_URL : apiUrl.trim());
        entity.setWebhookSecret(webhookSecret);
        entity.setWebhookUrl(webhookUrl);
        entity.setWebhookAutoRegister(webhookAutoRegister);
        entity.setRequireActiveSubscriber(requireActiveSubscriber);
        entity.setWelcomeMessage(welcomeMessage);
        entity.setGoodbyeMessage(goodbyeMessage);
        entity.setActive(active);
        entity.setDefault(isDefault);
        return entity;
    }
}
