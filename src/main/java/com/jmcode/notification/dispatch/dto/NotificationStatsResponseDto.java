package com.jmcode.notification.dispatch.dto;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationStatus;

import java.util.Map;

/**
 * Resumen agregado del historial de notificaciones (totales por estado y por canal).
 */
public record NotificationStatsResponseDto(
        long total,
        Map<NotificationStatus, Long> byStatus,
        Map<ChannelType, Long> byChannel
) {
}
