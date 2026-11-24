package com.jmcode.notification.whatsapp.dto;

import java.util.Map;

/** Código de emparejamiento (formato {@code DLSR-R19N}) a teclear en el móvil. */
public record WhatsAppPairCodeResponseDto(
        String deviceId,
        String pairCode
) {
    public static WhatsAppPairCodeResponseDto from(Map<String, Object> results) {
        return new WhatsAppPairCodeResponseDto(
                GowaResponses.toText(results.get("device_id")),
                GowaResponses.toText(results.get("pair_code")));
    }
}
