package com.jmcode.notification.email.dto;

import com.jmcode.notification.email.EmailTemplate;

import java.time.Instant;

public record EmailTemplateResponseDto(
        Long id,
        Long companyId,
        String companyCode,
        String name,
        String subject,
        String content,
        String contentType,
        String variables,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmailTemplateResponseDto from(EmailTemplate template) {
        return new EmailTemplateResponseDto(
                template.getId(),
                template.getCompany() == null ? null : template.getCompany().getId(),
                template.getCompany() == null ? null : template.getCompany().getCode(),
                template.getName(),
                template.getSubject(),
                template.getContent(),
                template.getContentType(),
                template.getVariables(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
