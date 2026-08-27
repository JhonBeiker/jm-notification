package com.jmcode.notification.email.dto;

import com.jmcode.notification.email.EmailTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailTemplateRequestDto(

        /** Empresa propietaria. Obligatorio para SUPER_ADMIN; el ADMIN de empresa usa siempre la suya. */
        Long companyId,

        @NotBlank @Size(max = 128) String name,

        @NotBlank @Size(max = 255) String subject,

        @NotBlank String content,

        @Pattern(regexp = "(?i)text|html|", message = "contentType must be 'text' or 'html'")
        String contentType,

        @Size(max = 512) String variables,

        boolean active
) {

    /** {@code contentType} vacío lo deduce el servicio a partir del contenido. */
    public EmailTemplate applyTo(EmailTemplate entity) {
        entity.setName(name.trim());
        entity.setSubject(subject);
        entity.setContent(content);
        entity.setContentType(contentType == null || contentType.isBlank() ? null : contentType.trim().toLowerCase());
        entity.setVariables(variables);
        entity.setActive(active);
        return entity;
    }
}
