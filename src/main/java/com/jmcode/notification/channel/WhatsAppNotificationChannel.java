package com.jmcode.notification.channel;

import com.jmcode.notification.config.NotificationProperties;
import com.jmcode.notification.domain.ChannelType;
import com.jmcode.notification.domain.NotificationRequest;
import com.jmcode.notification.domain.NotificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WhatsAppNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationChannel.class);

    private final RestClient restClient;
    private final NotificationProperties.WhatsApp properties;

    public WhatsAppNotificationChannel(RestClient.Builder restClientBuilder, NotificationProperties properties) {
        this.properties = properties.whatsapp();
        this.restClient = restClientBuilder.baseUrl(this.properties.apiUrl()).build();
    }

    @Override
    public ChannelType supports() {
        return ChannelType.WHATSAPP;
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled()
                && StringUtils.hasText(properties.phoneNumberId())
                && StringUtils.hasText(properties.accessToken());
    }

    @Override
    @SuppressWarnings("unchecked")
    public NotificationResult send(NotificationRequest request) {
        if (!isEnabled()) {
            return NotificationResult.skipped(supports(), request.to(), "WhatsApp channel is disabled or misconfigured");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", normalizePhone(request.to()));
            body.put("type", "text");
            body.put("text", Map.of("preview_url", false, "body", request.message()));

            Map<String, Object> response = restClient.post()
                    .uri("/{phoneNumberId}/messages", properties.phoneNumberId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.accessToken())
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String messageId = extractMessageId(response);
            log.info("WhatsApp message sent to {} id={}", request.to(), messageId);
            return NotificationResult.sent(supports(), request.to(), messageId);
        } catch (Exception ex) {
            log.error("Failed to send WhatsApp to {}: {}", request.to(), ex.getMessage());
            return NotificationResult.failed(supports(), request.to(), ex.getMessage());
        }
    }

    private static String normalizePhone(String to) {
        return to == null ? "" : to.replaceAll("[^+\\d]", "");
    }

    @SuppressWarnings("unchecked")
    private static String extractMessageId(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object messages = response.get("messages");
        if (messages instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> first) {
            Object id = first.get("id");
            return id != null ? id.toString() : null;
        }
        return null;
    }
}
