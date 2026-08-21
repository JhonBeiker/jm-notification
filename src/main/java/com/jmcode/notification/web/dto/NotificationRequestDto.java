package com.jmcode.notification.web.dto;

import com.jmcode.notification.domain.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record NotificationRequestDto(
        @NotNull ChannelType channel,
        @NotBlank String to,
        String subject,
        @NotBlank String message,
        String clientCode,
        Map<String, String> metadata,
        String templateName,
        Map<String, String> variables
) {
}