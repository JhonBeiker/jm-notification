package com.jmcode.notification.service;

import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.domain.ChannelType;
import com.jmcode.notification.domain.NotificationRequest;
import com.jmcode.notification.domain.NotificationResult;
import com.jmcode.notification.domain.NotificationStatus;
import com.jmcode.notification.web.dto.BulkNotificationRequestDto;
import com.jmcode.notification.web.dto.NotificationRequestDto;
import com.jmcode.notification.web.error.ChannelNotFoundException;
import com.jmcode.notification.web.error.NotificationSendException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final Map<ChannelType, NotificationChannel> channels;

    public NotificationService(List<NotificationChannel> channelList) {
        this.channels = new EnumMap<>(ChannelType.class);
        for (NotificationChannel channel : channelList) {
            this.channels.put(channel.supports(), channel);
        }
    }

    public NotificationResult send(NotificationRequestDto dto) {
        NotificationChannel channel = resolve(dto.channel());
        NotificationRequest request = toDomain(dto);
        NotificationResult result = channel.send(request);
        if (result.status() == NotificationStatus.FAILED) {
            throw new NotificationSendException(result);
        }
        return result;
    }

    public List<NotificationResult> sendBulk(BulkNotificationRequestDto dto) {
        return dto.notifications().stream().map(this::sendSafe).toList();
    }

    public Map<ChannelType, Boolean> channelStatus() {
        Map<ChannelType, Boolean> status = new EnumMap<>(ChannelType.class);
        for (ChannelType type : ChannelType.values()) {
            NotificationChannel channel = channels.get(type);
            status.put(type, channel != null && channel.isEnabled());
        }
        return status;
    }

    private NotificationResult sendSafe(NotificationRequestDto dto) {
        try {
            return send(dto);
        } catch (NotificationSendException ex) {
            return ex.getResult();
        }
    }

    private NotificationChannel resolve(ChannelType type) {
        NotificationChannel channel = channels.get(type);
        if (channel == null) {
            throw new ChannelNotFoundException(type);
        }
        return channel;
    }

    private static NotificationRequest toDomain(NotificationRequestDto dto) {
        return new NotificationRequest(
                dto.channel(),
                dto.to(),
                dto.subject(),
                dto.message(),
                dto.clientCode(),
                dto.metadata(),
                dto.templateName(),
                dto.variables()
        );
    }
}
