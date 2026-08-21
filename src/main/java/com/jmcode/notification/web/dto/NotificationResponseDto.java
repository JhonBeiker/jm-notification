package com.jmcode.notification.web.dto;

import com.jmcode.notification.domain.ChannelType;
import com.jmcode.notification.domain.NotificationResult;
import com.jmcode.notification.domain.NotificationStatus;

import java.time.Instant;

public record NotificationResponseDto(
        ChannelType channel,
        NotificationStatus status,
        String to,
        String providerMessageId,
        String detail,
        Instant timestamp
) {
    public static NotificationResponseDto from(NotificationResult result) {
        return new NotificationResponseDto(
                result.channel(),
                result.status(),
                result.to(),
                result.providerMessageId(),
                result.detail(),
                result.timestamp()
        );
    }
}
