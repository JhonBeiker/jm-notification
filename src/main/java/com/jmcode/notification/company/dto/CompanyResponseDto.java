package com.jmcode.notification.company.dto;

import com.jmcode.notification.company.Company;

import java.time.Instant;

public record CompanyResponseDto(
        Long id,
        String code,
        String name,
        String taxId,
        String contactEmail,
        String contactPhone,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CompanyResponseDto from(Company company) {
        return new CompanyResponseDto(
                company.getId(),
                company.getCode(),
                company.getName(),
                company.getTaxId(),
                company.getContactEmail(),
                company.getContactPhone(),
                company.getNotes(),
                company.isActive(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }
}
