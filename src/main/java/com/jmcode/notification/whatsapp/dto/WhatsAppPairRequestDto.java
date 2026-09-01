package com.jmcode.notification.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WhatsAppPairRequestDto(

        /** Número en formato internacional, con o sin {@code +} (se normaliza a dígitos). */
        @NotBlank
        @Pattern(regexp = "\\+?[\\d\\s-]{8,25}", message = "phoneNumber must be 8-20 digits, optionally starting with +")
        String phoneNumber
) {
}
