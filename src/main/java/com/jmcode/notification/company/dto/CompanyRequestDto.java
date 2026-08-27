package com.jmcode.notification.company.dto;

import com.jmcode.notification.company.Company;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequestDto(

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "code only allows letters, digits, dot, underscore and hyphen")
        String code,

        @NotBlank @Size(max = 191) String name,

        @Size(max = 64) String taxId,

        @Email @Size(max = 191) String contactEmail,

        @Size(max = 32) String contactPhone,

        @Size(max = 512) String notes,

        boolean active
) {

    public Company applyTo(Company entity) {
        entity.setCode(code);
        entity.setName(name.trim());
        entity.setTaxId(taxId);
        entity.setContactEmail(contactEmail == null ? null : contactEmail.trim().toLowerCase());
        entity.setContactPhone(contactPhone);
        entity.setNotes(notes);
        entity.setActive(active);
        return entity;
    }
}
