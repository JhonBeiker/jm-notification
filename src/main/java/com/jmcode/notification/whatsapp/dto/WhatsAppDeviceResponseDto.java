package com.jmcode.notification.whatsapp.dto;

import com.jmcode.notification.whatsapp.WhatsAppDevice;
import org.springframework.util.StringUtils;

import java.time.Instant;

public record WhatsAppDeviceResponseDto(
        Long id,
        Long companyId,
        String companyCode,
        String clientCode,
        /** Id del dispositivo dentro de GOWA (cabecera {@code X-Device-Id}). */
        String deviceId,
        String name,
        String apiUrl,
        String basicAuthUser,
        /** La password nunca se devuelve: sólo si la fila tiene una propia o usa la global. */
        boolean hasOwnCredentials,
        String phoneNumber,
        String status,
        boolean active,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
    public static WhatsAppDeviceResponseDto from(WhatsAppDevice device) {
        return new WhatsAppDeviceResponseDto(
                device.getId(),
                device.getCompany() == null ? null : device.getCompany().getId(),
                device.getCompany() == null ? null : device.getCompany().getCode(),
                device.getClientCode(),
                device.getDeviceId(),
                device.getName(),
                device.getApiUrl(),
                device.getBasicAuthUser(),
                StringUtils.hasText(device.getBasicAuthUser()),
                device.getPhoneNumber(),
                device.getStatus(),
                device.isActive(),
                device.isDefault(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
