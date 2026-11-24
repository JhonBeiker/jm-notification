package com.jmcode.notification.telegram.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterWebhookRequestDto(

        /** Base pública del túnel/dominio. Telegram sólo acepta HTTPS. */
        @Pattern(regexp = "https://.+|", message = "publicBaseUrl must be an HTTPS URL")
        @Size(max = 512)
        String publicBaseUrl,

        @Size(max = 64)
        String clientCode
) {
}
