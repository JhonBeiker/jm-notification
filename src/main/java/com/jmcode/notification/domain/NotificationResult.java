package com.jmcode.notification.domain;

import java.time.Instant;

public record NotificationResult(
        ChannelType channel,
        NotificationStatus status,
        String to,
        String providerMessageId,
        String detail,
        Instant timestamp
) {
    public static NotificationResult sent(ChannelType channel, String to, String providerMessageId) {
        return new NotificationResult(channel, NotificationStatus.SENT, to, providerMessageId, "OK", Instant.now());
    }

    public static NotificationResult failed(ChannelType channel, String to, String detail) {
        return new NotificationResult(channel, NotificationStatus.FAILED, to, null, detail, Instant.now());
    }

    public static NotificationResult skipped(ChannelType channel, String to, String detail) {
        return new NotificationResult(channel, NotificationStatus.SKIPPED, to, null, detail, Instant.now());
    }
}
