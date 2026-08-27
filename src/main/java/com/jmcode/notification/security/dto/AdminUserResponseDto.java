package com.jmcode.notification.security.dto;

import com.jmcode.notification.security.AdminUser;

import java.time.Instant;

public record AdminUserResponseDto(
        Long id,
        String email,
        String role,
        Long companyId,
        String companyCode,
        boolean active,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminUserResponseDto from(AdminUser user) {
        return new AdminUserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getCompany() == null ? null : user.getCompany().getId(),
                user.getCompany() == null ? null : user.getCompany().getCode(),
                user.isActive(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
