package com.jmcode.notification.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkNotificationRequestDto(
        @NotEmpty List<@Valid NotificationRequestDto> notifications
) {
}
