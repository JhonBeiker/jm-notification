package com.jmcode.notification.telegram;

import com.jmcode.notification.config.NotificationProperties;
import com.jmcode.notification.web.error.SubscriberNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TelegramSubscriberService {

    private static final Logger log = LoggerFactory.getLogger(TelegramSubscriberService.class);

    private final TelegramSubscriberRepository repository;
    private final TelegramBotClient botClient;
    private final NotificationProperties.Telegram properties;

    public TelegramSubscriberService(
            TelegramSubscriberRepository repository,
            TelegramBotClient botClient,
            NotificationProperties properties
    ) {
        this.repository = repository;
        this.botClient = botClient;
        this.properties = properties.telegram();
    }

    @Transactional
    public void handleUpdate(TelegramUpdatePayload update) {
        if (update == null) {
            return;
        }

        if (update.myChatMember() != null) {
            handleMembershipChange(update.myChatMember());
            return;
        }

        TelegramUpdatePayload.TelegramMessage message = update.effectiveMessage();
        if (message == null || message.chat() == null || message.chat().id() == null) {
            return;
        }

        String text = message.text() == null ? "" : message.text().trim();
        String command = extractCommand(text);
        String payload = extractStartPayload(text);

        if ("/start".equals(command)) {
            TelegramSubscriber subscriber = activate(message, payload);
            botClient.sendText(subscriber.getChatId(), properties.welcomeMessage());
            return;
        }

        if ("/stop".equals(command) || "/unsubscribe".equals(command)) {
            deactivate(message.chat().id());
            botClient.sendText(message.chat().id(), properties.goodbyeMessage());
            return;
        }

        if (repository.existsByChatIdAndActiveTrue(message.chat().id())) {
            touch(message);
        } else {
            log.debug("Ignoring message from non-active chat {}", message.chat().id());
        }
    }

    @Transactional
    public TelegramSubscriber activate(TelegramUpdatePayload.TelegramMessage message, String externalUserId) {
        Long chatId = message.chat().id();
        TelegramSubscriber subscriber = repository.findByChatId(chatId).orElseGet(TelegramSubscriber::new);
        Instant now = Instant.now();

        subscriber.setChatId(chatId);
        if (message.from() != null) {
            subscriber.setUserId(message.from().id());
            subscriber.setUsername(message.from().username());
            subscriber.setFirstName(message.from().firstName());
            subscriber.setLastName(message.from().lastName());
            subscriber.setLanguageCode(message.from().languageCode());
        } else {
            subscriber.setUsername(message.chat().username());
            subscriber.setFirstName(message.chat().firstName());
            subscriber.setLastName(message.chat().lastName());
        }

        if (StringUtils.hasText(externalUserId)) {
            subscriber.setExternalUserId(externalUserId.trim());
        }

        subscriber.setActive(true);
        subscriber.setStoppedAt(null);
        if (subscriber.getStartedAt() == null) {
            subscriber.setStartedAt(now);
        }
        subscriber.setLastInteractionAt(now);

        TelegramSubscriber saved = repository.save(subscriber);
        log.info("Telegram subscriber activated chatId={} externalUserId={}", chatId, saved.getExternalUserId());
        return saved;
    }

    @Transactional
    public void deactivate(Long chatId) {
        repository.findByChatId(chatId).ifPresent(subscriber -> {
            subscriber.setActive(false);
            subscriber.setStoppedAt(Instant.now());
            subscriber.setLastInteractionAt(Instant.now());
            repository.save(subscriber);
            log.info("Telegram subscriber deactivated chatId={}", chatId);
        });
    }

    @Transactional
    public void markBlocked(Long chatId) {
        deactivate(chatId);
    }

    @Transactional(readOnly = true)
    public List<TelegramSubscriber> listActive() {
        return repository.findByActiveTrueOrderByStartedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<TelegramSubscriber> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<TelegramSubscriber> findActiveByChatId(Long chatId) {
        return repository.findByChatId(chatId).filter(TelegramSubscriber::isActive);
    }

    @Transactional(readOnly = true)
    public Optional<TelegramSubscriber> findActiveByExternalUserId(String externalUserId) {
        if (!StringUtils.hasText(externalUserId)) {
            return Optional.empty();
        }
        return repository.findByExternalUserIdAndActiveTrue(externalUserId.trim());
    }

    @Transactional(readOnly = true)
    public TelegramSubscriber requireActive(Long chatId) {
        return findActiveByChatId(chatId)
                .orElseThrow(() -> new SubscriberNotFoundException(
                        "No active Telegram subscriber for chat_id=" + chatId + ". User must /start the bot first."
                ));
    }

    @Transactional(readOnly = true)
    public boolean isActiveChat(Long chatId) {
        return repository.existsByChatIdAndActiveTrue(chatId);
    }

    private void handleMembershipChange(TelegramUpdatePayload.TelegramChatMemberUpdated membership) {
        if (membership.chat() == null || membership.chat().id() == null || membership.newChatMember() == null) {
            return;
        }
        String status = membership.newChatMember().status();
        if (status == null) {
            return;
        }
        String normalized = status.toLowerCase(Locale.ROOT);
        if ("kicked".equals(normalized) || "left".equals(normalized)) {
            deactivate(membership.chat().id());
        }
    }

    private void touch(TelegramUpdatePayload.TelegramMessage message) {
        repository.findByChatId(message.chat().id()).ifPresent(subscriber -> {
            if (message.from() != null) {
                subscriber.setUsername(message.from().username());
                subscriber.setFirstName(message.from().firstName());
                subscriber.setLastName(message.from().lastName());
                subscriber.setLanguageCode(message.from().languageCode());
            }
            subscriber.setLastInteractionAt(Instant.now());
            repository.save(subscriber);
        });
    }

    private static String extractCommand(String text) {
        if (!StringUtils.hasText(text) || !text.startsWith("/")) {
            return "";
        }
        String first = text.split("\\s+")[0];
        int at = first.indexOf('@');
        return (at > 0 ? first.substring(0, at) : first).toLowerCase(Locale.ROOT);
    }

    private static String extractStartPayload(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String[] parts = text.trim().split("\\s+", 2);
        if (parts.length < 2) {
            return null;
        }
        String command = extractCommand(parts[0]);
        if (!"/start".equals(command)) {
            return null;
        }
        return parts[1].trim();
    }
}
