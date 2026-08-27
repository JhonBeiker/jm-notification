package com.jmcode.notification.security.dto;

public record LoginResponseDto(
        String token,
        long expiresInSeconds,
        String role,
        Long companyId,
        String companyCode
) {
}
