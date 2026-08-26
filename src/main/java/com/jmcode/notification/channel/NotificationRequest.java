package com.jmcode.notification.channel;

import java.util.List;
import java.util.Map;

public record NotificationRequest(
        ChannelType channel,
        String to,
        String subject,
        String message,
        String clientCode,
        Map<String, String> metadata,
        String templateName,
        Map<String, String> variables,
        List<Attachment> attachments
) {

    private static final String CLIENT_CODE_KEY = "clientCode";

    public NotificationRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /**
     * Código de cliente efectivo: el explícito del request, o el que venga en
     * {@code metadata.clientCode}, o {@code null} para usar la cuenta por defecto.
     */
    public String effectiveClientCode() {
        if (clientCode != null && !clientCode.isBlank()) {
            return clientCode.trim();
        }
        String fromMetadata = metadata.get(CLIENT_CODE_KEY);
        return fromMetadata == null || fromMetadata.isBlank() ? null : fromMetadata.trim();
    }

    public boolean hasTemplate() {
        return templateName != null && !templateName.isBlank();
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
}
