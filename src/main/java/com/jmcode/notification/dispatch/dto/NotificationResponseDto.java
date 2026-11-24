package com.jmcode.notification.dispatch.dto;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.channel.NotificationStatus;

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
