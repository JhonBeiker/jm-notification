package com.jmcode.notification.whatsapp.dto;

import java.util.Map;

/**
 * Estado del dispositivo en GOWA. {@code state} vale {@code logged_in} cuando ya se puede
 * enviar; {@code disconnected} o {@code connected} (conectado pero sin emparejar) significan
 * que aún falta escanear el QR o teclear el pair code.
 */
public record WhatsAppDeviceStatusResponseDto(
        String deviceId,
        String state,
        boolean loggedIn,
        String jid,
        String phoneNumber,
        String displayName
) {
    private static final String LOGGED_IN = "logged_in";

    public static WhatsAppDeviceStatusResponseDto from(Map<String, Object> results) {
        String jid = GowaResponses.toText(results.get("jid"));
        String state = GowaResponses.toText(results.get("state"));
        return new WhatsAppDeviceStatusResponseDto(
                GowaResponses.toText(results.get("id")),
                state,
                LOGGED_IN.equals(state),
                jid,
                phoneOf(jid),
                GowaResponses.toText(results.get("display_name")));
    }

    /** El JID trae el número por delante de la arroba: {@code 584263073306@s.whatsapp.net}. */
    private static String phoneOf(String jid) {
        if (jid == null || jid.isBlank()) {
            return null;
        }
        int at = jid.indexOf('@');
        return at > 0 ? jid.substring(0, at) : jid;
    }
}
