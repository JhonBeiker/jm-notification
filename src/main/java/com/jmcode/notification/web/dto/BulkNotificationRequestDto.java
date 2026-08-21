package com.jmcode.notification.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkNotificationRequestDto(
        @NotEmpty List<@Valid NotificationRequestDto> notifications
) {
}
