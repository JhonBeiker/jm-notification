package com.jmcode.notification.domain;

import java.util.Map;

public record NotificationRequest(
        ChannelType channel,
        String to,
        String subject,
        String message,
        String clientCode,
        Map<String, String> metadata,
        String templateName,
        java.util.Map<String, String> variables
) {
}