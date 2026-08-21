package com.jmcode.notification.channel;

import com.jmcode.notification.config.NotificationProperties;
import com.jmcode.notification.domain.ChannelType;
import com.jmcode.notification.domain.NotificationRequest;
import com.jmcode.notification.domain.NotificationResult;
import com.jmcode.notification.telegram.TelegramBotClient;
import com.jmcode.notification.telegram.TelegramSubscriberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Optional;

@Component
public class TelegramNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationChannel.class);

    private final TelegramBotClient botClient;
    private final TelegramSubscriberService subscriberService;
    private final NotificationProperties.Telegram properties;

    public TelegramNotificationChannel(
            TelegramBotClient botClient,
            TelegramSubscriberService subscriberService,
            NotificationProperties properties
    ) {
        this.botClient = botClient;
        this.subscriberService = subscriberService;
        this.properties = properties.telegram();
    }

    @Override
    public ChannelType supports() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled() && botClient.isConfigured();
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        if (!isEnabled()) {
            return NotificationResult.skipped(supports(), request.to(), "Telegram channel is disabled or misconfigured");
        }

        if (!botClient.isConfigured()) {
            return NotificationResult.failed(
                    supports(),
                    request.to(),
                    "Invalid TELEGRAM_BOT_TOKEN format. Expected '<botId>:<secret>' from @BotFather"
            );
        }

        Optional<ResolvedTarget> target = resolveTarget(request.to());
        if (target.isEmpty()) {
            return NotificationResult.failed(
                    supports(),
                    request.to(),
                    "Unable to resolve Telegram target. Use chat_id of an active subscriber, externalUserId, or deep-link payload from /start"
            );
        }

        ResolvedTarget resolved = target.get();
        if (properties.requireActiveSubscriber() && !resolved.active()) {
            return NotificationResult.failed(
                    supports(),
                    request.to(),
                    "User is not an active Telegram subscriber. They must open the bot and send /start first"
            );
        }

        try {
            String text = StringUtils.hasText(request.subject())
                    ? "*" + request.subject() + "*\n" + request.message()
                    : request.message();

            Map<String, Object> response = botClient.sendNotification(resolved.chatId().toString(), text);

            if (response != null && Boolean.FALSE.equals(response.get("ok"))) {
                String description = String.valueOf(response.getOrDefault("description", "Unknown Telegram error"));
                handleProviderError(resolved.chatId(), description);
                return NotificationResult.failed(supports(), resolved.chatId().toString(), description);
            }

            String messageId = extractMessageId(response);
            log.info("Telegram message sent to chat {} id={}", resolved.chatId(), messageId);
            return NotificationResult.sent(supports(), resolved.chatId().toString(), messageId);
        } catch (RestClientResponseException ex) {
            handleProviderError(resolved.chatId(), ex.getResponseBodyAsString());
            log.error("Failed to send Telegram to {}: {}", resolved.chatId(), ex.getMessage());
            return NotificationResult.failed(supports(), resolved.chatId().toString(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send Telegram to {}: {}", resolved.chatId(), ex.getMessage());
            return NotificationResult.failed(supports(), resolved.chatId().toString(), ex.getMessage());
        }
    }

    private Optional<ResolvedTarget> resolveTarget(String to) {
        if (!StringUtils.hasText(to)) {
            return Optional.empty();
        }

        String value = to.trim();
        if (value.startsWith("+")) {
            return Optional.empty();
        }

        if (value.chars().allMatch(ch -> ch == '-' || Character.isDigit(ch))) {
            try {
                Long chatId = Long.parseLong(value);
                boolean active = subscriberService.isActiveChat(chatId);
                if (!properties.requireActiveSubscriber()) {
                    return Optional.of(new ResolvedTarget(chatId, active));
                }
                return subscriberService.findActiveByChatId(chatId)
                        .map(sub -> new ResolvedTarget(sub.getChatId(), true));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        return subscriberService.findActiveByExternalUserId(value)
                .map(sub -> new ResolvedTarget(sub.getChatId(), true));
    }

    private void handleProviderError(Long chatId, String detail) {
        if (detail == null) {
            return;
        }
        String lower = detail.toLowerCase();
        if (lower.contains("blocked") || lower.contains("deactivated") || lower.contains("forbidden") || lower.contains("chat not found")) {
            subscriberService.markBlocked(chatId);
        }
    }

    private static String extractMessageId(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object result = response.get("result");
        if (result instanceof Map<?, ?> map) {
            Object messageId = map.get("message_id");
            return messageId != null ? messageId.toString() : null;
        }
        return null;
    }

    private record ResolvedTarget(Long chatId, boolean active) {
    }
}
