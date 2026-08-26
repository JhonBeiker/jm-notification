package com.jmcode.notification.dispatch.dto;

import com.jmcode.notification.channel.Attachment;
import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record NotificationRequestDto(

        @NotNull
        @Schema(description = "Canal de envío", example = "EMAIL")
        ChannelType channel,

        @NotBlank
        @Size(max = 512)
        @Schema(description = "Destinatario: email, teléfono, chat_id o externalUserId", example = "user-99")
        String to,

        @Size(max = 512)
        String subject,

        @NotBlank
        @Size(max = 100_000, message = "message must not exceed 100000 characters")
        String message,

        @Size(max = 64)
        @Schema(description = "Cuenta a usar. Si se omite se usa la marcada por defecto")
        String clientCode,

        Map<String, String> metadata,

        @Size(max = 128)
        String templateName,

        Map<String, String> variables,

        @Valid
        @Size(max = 10, message = "at most 10 attachments are allowed")
        List<Attachment> attachments
) {

    public NotificationRequest toDomain() {
        return new NotificationRequest(channel, to, subject, message, clientCode,
                metadata, templateName, variables, attachments);
    }
}
