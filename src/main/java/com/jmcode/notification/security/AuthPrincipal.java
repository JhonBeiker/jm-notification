package com.jmcode.notification.security;

/** {@code companyId} es {@code null} para SUPER_ADMIN: no está limitado a ninguna empresa. */
public record AuthPrincipal(Long userId, String email, Role role, Long companyId) {
}
