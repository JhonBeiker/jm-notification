package com.jmcode.notification.telegram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LinkExternalUserRequestDto(
        @NotNull Long chatId,
        @NotBlank String externalUserId
) {
}
