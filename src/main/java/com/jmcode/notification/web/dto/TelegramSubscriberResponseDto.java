package com.jmcode.notification.web.dto;

import com.jmcode.notification.telegram.TelegramSubscriber;

import java.time.Instant;

public record TelegramSubscriberResponseDto(
        Long id,
        Long chatId,
        Long userId,
        String username,
        String firstName,
        String lastName,
        String languageCode,
        String externalUserId,
        boolean active,
        Instant startedAt,
        Instant stoppedAt,
        Instant lastInteractionAt
) {
    public static TelegramSubscriberResponseDto from(TelegramSubscriber subscriber) {
        return new TelegramSubscriberResponseDto(
                subscriber.getId(),
                subscriber.getChatId(),
                subscriber.getUserId(),
                subscriber.getUsername(),
                subscriber.getFirstName(),
                subscriber.getLastName(),
                subscriber.getLanguageCode(),
                subscriber.getExternalUserId(),
                subscriber.isActive(),
                subscriber.getStartedAt(),
                subscriber.getStoppedAt(),
                subscriber.getLastInteractionAt()
        );
    }
}
