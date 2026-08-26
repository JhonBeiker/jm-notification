package com.jmcode.notification.security.dto;

import com.jmcode.notification.security.ApiClient;

public record CreatedApiClientResponseDto(ApiClientResponseDto client, String apiKey) {
    public static CreatedApiClientResponseDto from(ApiClient client, String plainKey) {
        return new CreatedApiClientResponseDto(ApiClientResponseDto.from(client), plainKey);
    }
}
