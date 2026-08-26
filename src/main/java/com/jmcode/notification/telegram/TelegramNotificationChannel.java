package com.jmcode.notification.telegram;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TelegramNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationChannel.class);

    /** Respuestas de Telegram que indican que el chat ya no es alcanzable. */
    private static final String[] UNREACHABLE_MARKERS = {"blocked", "deactivated", "forbidden", "chat not found"};

    private final TelegramBotAccountService accountService;
    private final TelegramBotAccountManager accountManager;
    private final TelegramSubscriberService subscriberService;
    private final NotificationProperties properties;

    @Override
    public ChannelType supports() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public boolean isEnabled() {
        return properties.telegram().enabled();
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        if (!isEnabled()) {
            return NotificationResult.skipped(supports(), request.to(), "Telegram channel is disabled");
        }

        TelegramBotAccount account;
        try {
            account = accountService.resolveAccount(request.effectiveClientCode());
        } catch (RuntimeException ex) {
            return NotificationResult.failed(supports(), request.to(), ex.getMessage());
        }

        TelegramBotClient botClient = accountManager.getClient(account);
        if (!botClient.isConfigured()) {
            return NotificationResult.failed(supports(), request.to(),
                    "Telegram bot token not configured for clientCode: " + account.getClientCode());
        }

        boolean requireActive = account.isRequireActiveSubscriber();
        Optional<ResolvedTarget> target = resolveTarget(request.to(), requireActive);
        if (target.isEmpty()) {
            return NotificationResult.failed(supports(), request.to(),
                    "Unable to resolve Telegram target. Use the chat_id of an active subscriber, "
                            + "a linked externalUserId, or the deep-link payload from /start");
        }

        ResolvedTarget resolved = target.get();
        if (requireActive && !resolved.active()) {
            return NotificationResult.failed(supports(), request.to(),
                    "User is not an active Telegram subscriber. They must open the bot and send /start first");
        }

        String chatId = resolved.chatId().toString();
        try {
            String text = StringUtils.hasText(request.subject())
                    ? "*" + request.subject() + "*\n" + request.message()
                    : request.message();

            Map<String, Object> response = request.hasAttachments()
                    ? botClient.sendDocument(chatId, text, request.attachments())
                    : botClient.sendNotification(chatId, text);

            if (Boolean.FALSE.equals(response.get("ok"))) {
                String description = String.valueOf(response.getOrDefault("description", "Unknown Telegram error"));
                handleProviderError(resolved.chatId(), description);
                return NotificationResult.failed(supports(), chatId, description);
            }

            String messageId = extractMessageId(response);
            log.info("Telegram message sent to chat {} id={} clientCode={} attachments={}",
                    chatId, messageId, account.getClientCode(), request.attachments().size());
            return NotificationResult.sent(supports(), chatId, messageId);
        } catch (RestClientResponseException ex) {
            handleProviderError(resolved.chatId(), ex.getResponseBodyAsString());
            log.error("Telegram rejected message to {} clientCode={}", chatId, account.getClientCode(), ex);
            return NotificationResult.failed(supports(), chatId, ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send Telegram message to {} clientCode={}", chatId, account.getClientCode(), ex);
            return NotificationResult.failed(supports(), chatId, ex.getMessage());
        }
    }

    /**
     * Acepta un {@code chat_id} numérico o un {@code externalUserId} previamente vinculado.
     * Un valor que empieza por {@code +} es un teléfono: Telegram no permite escribir por número.
     */
    private Optional<ResolvedTarget> resolveTarget(String to, boolean requireActive) {
        if (!StringUtils.hasText(to)) {
            return Optional.empty();
        }
        String value = to.trim();
        if (value.startsWith("+")) {
            return Optional.empty();
        }

        if (isNumericChatId(value)) {
            try {
                Long chatId = Long.parseLong(value);
                if (!requireActive) {
                    return Optional.of(new ResolvedTarget(chatId, subscriberService.isActiveChat(chatId)));
                }
                return subscriberService.findActiveByChatId(chatId)
                        .map(subscriber -> new ResolvedTarget(subscriber.getChatId(), true));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }

        return subscriberService.findActiveByExternalUserId(value)
                .map(subscriber -> new ResolvedTarget(subscriber.getChatId(), true));
    }

    private static boolean isNumericChatId(String value) {
        return value.chars().allMatch(ch -> ch == '-' || Character.isDigit(ch));
    }

    private void handleProviderError(Long chatId, String detail) {
        if (detail == null) {
            return;
        }
        String lower = detail.toLowerCase(Locale.ROOT);
        for (String marker : UNREACHABLE_MARKERS) {
            if (lower.contains(marker)) {
                subscriberService.markBlocked(chatId);
                return;
            }
        }
    }

    private static String extractMessageId(Map<String, Object> response) {
        if (response.get("result") instanceof Map<?, ?> result) {
            Object messageId = result.get("message_id");
            return messageId != null ? messageId.toString() : null;
        }
        return null;
    }

    private record ResolvedTarget(Long chatId, boolean active) {
    }
}
