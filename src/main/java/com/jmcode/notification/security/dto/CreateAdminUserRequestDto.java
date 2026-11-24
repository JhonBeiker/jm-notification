package com.jmcode.notification.security.dto;

import com.jmcode.notification.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAdminUserRequestDto(

        @NotBlank @Email @Size(max = 191) String email,

        @NotBlank @Size(min = 12, max = 128) String password,

        @NotNull Role role,

        /** Obligatorio para ADMIN, debe ir vacío para SUPER_ADMIN. */
        Long companyId
) {
}
