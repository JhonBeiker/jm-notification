package com.jmcode.notification.dispatch.dto;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.channel.NotificationStatus;

import java.time.Instant;

public record NotificationResponseDto(
        Long id,
        ChannelType channel,
        NotificationStatus status,
        String to,
        String providerMessageId,
        String detail,
        Instant timestamp
) {
    public static NotificationResponseDto from(NotificationResult result) {
        return from(result, null);
    }

    public static NotificationResponseDto from(NotificationResult result, Long id) {
        return new NotificationResponseDto(
                id,
                result.channel(),
                result.status(),
                result.to(),
                result.providerMessageId(),
                result.detail(),
                result.timestamp()
        );
    }
}
