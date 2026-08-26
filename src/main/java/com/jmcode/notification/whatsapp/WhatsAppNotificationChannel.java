package com.jmcode.notification.whatsapp;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
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
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

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
    public NotificationResult send(NotificationRequest request) {
        if (!isEnabled()) {
            return NotificationResult.skipped(supports(), request.to(), "WhatsApp channel is disabled or misconfigured");
        }
        if (request.hasAttachments()) {
            log.warn("WhatsApp channel does not support attachments yet; sending text only to {}", request.to());
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
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .body(body)
                    .retrieve()
                    .body(RESPONSE_TYPE);

            String messageId = extractMessageId(response);
            log.info("WhatsApp message sent to {} id={}", request.to(), messageId);
            return NotificationResult.sent(supports(), request.to(), messageId);
        } catch (Exception ex) {
            log.error("Failed to send WhatsApp message to {}", request.to(), ex);
            return NotificationResult.failed(supports(), request.to(), ex.getMessage());
        }
    }

    private static String normalizePhone(String to) {
        return to == null ? "" : to.replaceAll("[^+\\d]", "");
    }

    private static String extractMessageId(Map<String, Object> response) {
        if (response != null
                && response.get("messages") instanceof List<?> messages
                && !messages.isEmpty()
                && messages.get(0) instanceof Map<?, ?> first) {
            Object id = first.get("id");
            return id != null ? id.toString() : null;
        }
        return null;
    }
}
