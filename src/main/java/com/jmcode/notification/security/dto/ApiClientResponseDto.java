package com.jmcode.notification.security.dto;

import com.jmcode.notification.security.ApiClient;

import java.time.Instant;

public record ApiClientResponseDto(
        Long id,
        String name,
        String contactEmail,
        String apiKeyPrefix,
        boolean active,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ApiClientResponseDto from(ApiClient client) {
        return new ApiClientResponseDto(
                client.getId(),
                client.getName(),
                client.getContactEmail(),
                client.getApiKeyPrefix(),
                client.isActive(),
                client.getLastUsedAt(),
                client.getCreatedAt(),
                client.getUpdatedAt());
    }
}
