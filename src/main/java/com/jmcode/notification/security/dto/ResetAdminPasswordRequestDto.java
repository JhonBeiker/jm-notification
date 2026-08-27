package com.jmcode.notification.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetAdminPasswordRequestDto(
        @NotBlank @Size(min = 12, max = 128) String password
) {
}
