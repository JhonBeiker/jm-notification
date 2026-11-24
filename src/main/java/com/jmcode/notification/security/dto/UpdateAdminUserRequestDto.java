package com.jmcode.notification.security.dto;

import com.jmcode.notification.security.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateAdminUserRequestDto(
        @NotNull Role role,
        Long companyId,
        boolean active
) {
}
