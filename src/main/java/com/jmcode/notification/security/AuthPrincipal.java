package com.jmcode.notification.security;

public record AuthPrincipal(Long userId, String email, Role role) {
}
