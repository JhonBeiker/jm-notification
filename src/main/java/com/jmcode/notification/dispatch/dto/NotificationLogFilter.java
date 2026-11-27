package com.jmcode.notification.dispatch.dto;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationStatus;

import java.time.Instant;

/**
 * Filtros opcionales para consultar el historial de notificaciones.
 */
public record NotificationLogFilter(
        Long companyId,
        ChannelType channel,
        NotificationStatus status,
        String recipient,
        String clientCode,
        Instant from,
        Instant until
) {
}
