package com.jmcode.notification.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record EmailAccountRequestDto(
        @NotBlank String clientCode,
        String name,
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @NotBlank String username,
        @NotBlank String password,
        @Email String fromAddress,
        String fromName,
        @Email String replyTo,
        String protocol,
        boolean auth,
        boolean starttlsEnable,
        boolean starttlsRequired,
        boolean sslEnable,
        boolean active,
        boolean isDefault,
        int connectionTimeoutMs,
        int timeoutMs,
        int writeTimeoutMs
) {
}
