package com.jmcode.notification.dispatch;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.common.ChannelNotFoundException;
import com.jmcode.notification.common.NotificationSendException;
import com.jmcode.notification.dispatch.dto.BulkNotificationRequestDto;
import com.jmcode.notification.dispatch.dto.NotificationRequestDto;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Enruta cada petición al {@link NotificationChannel} que declara soportar su
 * {@link ChannelType}. Los canales se descubren por inyección: añadir un canal nuevo
 * sólo requiere un {@code @Component} que implemente la interfaz.
 */
@Service
public class NotificationService {

    private final Map<ChannelType, NotificationChannel> channels;

    public NotificationService(List<NotificationChannel> channelList) {
        Map<ChannelType, NotificationChannel> byType = new EnumMap<>(ChannelType.class);
        for (NotificationChannel channel : channelList) {
            NotificationChannel previous = byType.put(channel.supports(), channel);
            if (previous != null) {
                throw new IllegalStateException("Duplicate NotificationChannel for %s: %s and %s"
                        .formatted(channel.supports(), previous.getClass().getName(), channel.getClass().getName()));
            }
        }
        this.channels = Map.copyOf(byType);
    }

    /**
     * Envía una notificación. Un resultado {@code FAILED} se propaga como excepción para
     * que el cliente reciba 502; {@code SKIPPED} (canal deshabilitado) se devuelve tal cual.
     */
    public NotificationResult send(NotificationRequestDto dto) {
        NotificationChannel channel = resolve(dto.channel());
        NotificationResult result = channel.send(dto.toDomain());
        if (result.status() == NotificationStatus.FAILED) {
            throw new NotificationSendException(result);
        }
        return result;
    }

    /** El envío masivo nunca aborta: cada elemento devuelve su propio estado. */
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
        } catch (ChannelNotFoundException ex) {
            return NotificationResult.failed(dto.channel(), dto.to(), ex.getMessage());
        } catch (AccessDeniedException ex) {
            // En un envío suelto esto sale como 403; dentro del lote es un elemento fallido
            // más, para no tumbar el resto por un clientCode que no es de esta API Key.
            return NotificationResult.failed(dto.channel(), dto.to(), ex.getMessage());
        }
    }

    private NotificationChannel resolve(ChannelType type) {
        NotificationChannel channel = channels.get(type);
        if (channel == null) {
            throw new ChannelNotFoundException(type);
        }
        return channel;
    }
}
