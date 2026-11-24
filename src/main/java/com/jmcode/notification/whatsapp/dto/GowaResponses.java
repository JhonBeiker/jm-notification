package com.jmcode.notification.whatsapp.dto;

/**
 * Lectura defensiva del {@code results} de GOWA, que llega como {@code Map<String, Object>}:
 * un campo ausente vale {@code null} en vez de romper el mapeo.
 */
final class GowaResponses {

    private GowaResponses() {
    }

    static String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static Integer toInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
