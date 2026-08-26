package com.jmcode.notification.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiClientRequestDto(
        @NotBlank @Size(max = 128) String name,
        @Email String contactEmail
) {
}
