package com.jmcode.notification.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "notification.auth")
public record AuthProperties(
        @DefaultValue("") String adminEmail,
        @DefaultValue("") String adminPassword,
        @DefaultValue("") String jwtSecret,
        @DefaultValue("12h") Duration jwtTtl
) {
}
