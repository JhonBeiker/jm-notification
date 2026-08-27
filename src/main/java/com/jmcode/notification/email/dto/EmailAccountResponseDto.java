package com.jmcode.notification.email.dto;

import com.jmcode.notification.email.EmailAccount;

import java.time.Instant;

public record EmailAccountResponseDto(
        Long id,
        Long companyId,
        String companyCode,
        String clientCode,
        String name,
        String host,
        int port,
        String username,
        String fromAddress,
        String fromName,
        String replyTo,
        String protocol,
        boolean auth,
        boolean starttlsEnable,
        boolean sslEnable,
        boolean active,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmailAccountResponseDto from(EmailAccount account) {
        return new EmailAccountResponseDto(
                account.getId(),
                account.getCompany() == null ? null : account.getCompany().getId(),
                account.getCompany() == null ? null : account.getCompany().getCode(),
                account.getClientCode(),
                account.getName(),
                account.getHost(),
                account.getPort(),
                account.getUsername(),
                account.getFromAddress(),
                account.getFromName(),
                account.getReplyTo(),
                account.getProtocol(),
                account.isAuth(),
                account.isStarttlsEnable(),
                account.isSslEnable(),
                account.isActive(),
                account.isDefault(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
