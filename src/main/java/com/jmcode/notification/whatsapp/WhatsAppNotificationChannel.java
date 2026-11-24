package com.jmcode.notification.whatsapp;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Envío por WhatsApp a través de una instancia GOWA. El dispositivo se resuelve por
 * {@code clientCode} igual que en Telegram: cada empresa manda por el suyo.
 *
 * <p>El destinatario es un número en formato internacional (con o sin {@code +}) o un JID
 * completo ({@code 584263073306@s.whatsapp.net}, {@code ...@g.us} para grupos).
 */
@Component
@RequiredArgsConstructor
public class WhatsAppNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationChannel.class);

    private final WhatsAppDeviceService deviceService;
    private final WhatsAppDeviceManager deviceManager;
    private final NotificationProperties properties;

    @Override
    public ChannelType supports() {
        return ChannelType.WHATSAPP;
    }

    @Override
    public boolean isEnabled() {
        return properties.whatsapp().enabled();
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        if (!isEnabled()) {
            return NotificationResult.skipped(supports(), request.to(), "WhatsApp channel is disabled");
        }
        if (request.hasAttachments()) {
            log.warn("WhatsApp channel does not support attachments yet; sending text only to {}", request.to());
        }

        WhatsAppDevice device;
        try {
            device = deviceService.resolveDevice(request.effectiveClientCode());
        } catch (AccessDeniedException ex) {
            // Un clientCode de otra empresa es un 403, no un fallo del proveedor: se propaga.
            throw ex;
        } catch (RuntimeException ex) {
            return NotificationResult.failed(supports(), request.to(), ex.getMessage());
        }

        String to = normalizeRecipient(request.to());
        if (!StringUtils.hasText(to)) {
            return NotificationResult.failed(supports(), request.to(),
                    "Invalid WhatsApp recipient. Use an international phone number or a JID");
        }
        if (!StringUtils.hasText(device.getDeviceId())) {
            return NotificationResult.failed(supports(), to,
                    "WhatsApp device not provisioned in GOWA for clientCode: " + device.getClientCode());
        }

        GowaClient client = deviceManager.getClient(device);
        if (!client.isConfigured()) {
            return NotificationResult.failed(supports(), to,
                    "GOWA apiUrl not configured for clientCode: " + device.getClientCode());
        }

        try {
            String text = StringUtils.hasText(request.subject())
                    ? "*" + request.subject() + "*\n" + request.message()
                    : request.message();

            Map<String, Object> results = client.sendText(device.getDeviceId(), to, text);
            String messageId = results.get("message_id") == null ? null : String.valueOf(results.get("message_id"));
            log.info("WhatsApp message sent to {} id={} device={} clientCode={}",
                    to, messageId, device.getDeviceId(), device.getClientCode());
            return NotificationResult.sent(supports(), to, messageId);
        } catch (Exception ex) {
            // GOWA responde 401 "you are not logged in" mientras el dispositivo no está
            // emparejado: es la causa más común y conviene que se lea tal cual en el detalle.
            log.error("Failed to send WhatsApp message to {} device={}", to, device.getDeviceId(), ex);
            return NotificationResult.failed(supports(), to, ex.getMessage());
        }
    }

    /**
     * GOWA espera el número sin {@code +} ni separadores, o un JID tal cual. Un valor que ya
     * trae {@code @} se respeta para poder escribir a grupos y a JIDs con sufijo.
     */
    private static String normalizeRecipient(String to) {
        if (!StringUtils.hasText(to)) {
            return "";
        }
        String value = to.trim();
        if (value.contains("@")) {
            return value;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() < 8 ? "" : digits;
    }
}
