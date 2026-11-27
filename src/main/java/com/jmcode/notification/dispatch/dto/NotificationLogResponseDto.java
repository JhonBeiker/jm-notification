package com.jmcode.notification.dispatch.dto;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.dispatch.entity.NotificationLog;

import java.time.Instant;

public record NotificationLogResponseDto(
        Long id,
        Long companyId,
        String companyCode,
        ChannelType channel,
        NotificationStatus status,
        String to,
        String subject,
        String clientCode,
        String templateName,
        String providerMessageId,
        String detail,
        int attempts,
        int maxAttempts,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant createdAt
) {
    public static NotificationLogResponseDto from(NotificationLog log) {
        return new NotificationLogResponseDto(
                log.getId(),
                log.getCompany() == null ? null : log.getCompany().getId(),
                log.getCompany() == null ? null : log.getCompany().getCode(),
                log.getChannel(),
                log.getStatus(),
                log.getRecipient(),
                log.getSubject(),
                log.getClientCode(),
                log.getTemplateName(),
                log.getProviderMessageId(),
                log.getDetail(),
                log.getAttempts(),
                log.getMaxAttempts(),
                log.getNextAttemptAt(),
                log.getLastAttemptAt(),
                log.getCreatedAt()
        );
    }
}
