package com.jmcode.notification.email.dto;

import com.jmcode.notification.email.EmailAccount;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EmailAccountRequestDto(
        @NotBlank @Size(max = 64) String clientCode,
        /** Empresa propietaria. Obligatorio para SUPER_ADMIN; el ADMIN de empresa usa siempre la suya. */
        Long companyId,
        @Size(max = 128) String name,
        @NotBlank @Size(max = 255) String host,
        @Min(1) @Max(65535) int port,
        @NotBlank @Size(max = 255) String username,
        @NotBlank String password,
        @Email @Size(max = 255) String fromAddress,
        @Size(max = 128) String fromName,
        @Email @Size(max = 255) String replyTo,
        @Size(max = 16) String protocol,
        boolean auth,
        boolean starttlsEnable,
        boolean starttlsRequired,
        boolean sslEnable,
        boolean active,
        boolean isDefault,
        @Positive Integer connectionTimeoutMs,
        @Positive Integer timeoutMs,
        @Positive Integer writeTimeoutMs
) {

    private static final String DEFAULT_PROTOCOL = "smtp";

    /** Vuelca el DTO sobre la entidad (nueva o existente). Los timeouts nulos conservan el valor actual. */
    public EmailAccount applyTo(EmailAccount entity) {
        entity.setClientCode(clientCode.trim());
        entity.setName(name);
        entity.setHost(host.trim());
        entity.setPort(port);
        entity.setUsername(username.trim());
        entity.setPassword(password);
        entity.setFromAddress(fromAddress);
        entity.setFromName(fromName);
        entity.setReplyTo(replyTo);
        entity.setProtocol(protocol == null || protocol.isBlank() ? DEFAULT_PROTOCOL : protocol.trim());
        entity.setAuth(auth);
        entity.setStarttlsEnable(starttlsEnable);
        entity.setStarttlsRequired(starttlsRequired);
        entity.setSslEnable(sslEnable);
        entity.setActive(active);
        entity.setDefault(isDefault);
        if (connectionTimeoutMs != null) {
            entity.setConnectionTimeoutMs(connectionTimeoutMs);
        }
        if (timeoutMs != null) {
            entity.setTimeoutMs(timeoutMs);
        }
        if (writeTimeoutMs != null) {
            entity.setWriteTimeoutMs(writeTimeoutMs);
        }
        return entity;
    }
}
