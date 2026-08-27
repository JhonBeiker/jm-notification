package com.jmcode.notification.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiClientRequestDto(
        @NotBlank @Size(max = 128) String name,
        @Email String contactEmail,
        /**
         * Empresa dueña de la clave: acota los envíos a las cuentas de esa empresa.
         * Obligatorio para SUPER_ADMIN; el ADMIN de una empresa usa siempre la suya.
         */
        Long companyId
) {
}
