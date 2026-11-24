package com.jmcode.notification.whatsapp.dto;

import java.util.Map;

/**
 * QR pendiente de escanear. GOWA renderiza el PNG él mismo y devuelve su URL en
 * {@code qr_link} (la sirve sin Basic Auth, así que vale directamente en un {@code <img>});
 * caduca en {@code qrDuration} segundos.
 */
public record WhatsAppQrResponseDto(
        String deviceId,
        String qrLink,
        Integer qrDuration
) {
    public static WhatsAppQrResponseDto from(Map<String, Object> results) {
        return new WhatsAppQrResponseDto(
                GowaResponses.toText(results.get("device_id")),
                GowaResponses.toText(results.get("qr_link")),
                GowaResponses.toInt(results.get("qr_duration")));
    }
}
