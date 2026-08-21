package com.jmcode.notification.telegram;

import com.jmcode.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotClient.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^\\d+:[A-Za-z0-9_-]+$");

    private final RestClient restClient;
    private final NotificationProperties.Telegram properties;

    public TelegramBotClient(RestClient.Builder restClientBuilder, NotificationProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties.telegram();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.botToken()) && TOKEN_PATTERN.matcher(properties.botToken().trim()).matches();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> sendText(Long chatId, String text) {
        if (!isConfigured() || chatId == null || !StringUtils.hasText(text)) {
            return Map.of();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(methodUri("sendMessage"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (Exception ex) {
            log.warn("Failed to send Telegram helper message to {}: {}", chatId, ex.getMessage());
            return Map.of("ok", false, "description", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> sendNotification(String chatId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "Markdown");

        return restClient.post()
                .uri(methodUri("sendMessage"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> setWebhook(String webhookUrl, String secretToken) {
        ensureConfigured();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", webhookUrl);
        body.put("drop_pending_updates", true);
        body.put("allowed_updates", new String[]{"message", "edited_message", "my_chat_member"});
        if (StringUtils.hasText(secretToken)) {
            body.put("secret_token", secretToken);
        }

        Map<String, Object> response = restClient.post()
                .uri(methodUri("setWebhook"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        log.info("Telegram setWebhook url={} ok={}", webhookUrl, response != null ? response.get("ok") : null);
        return response == null ? Map.of() : response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> deleteWebhook() {
        ensureConfigured();

        Map<String, Object> body = Map.of("drop_pending_updates", true);
        Map<String, Object> response = restClient.post()
                .uri(methodUri("deleteWebhook"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        log.info("Telegram deleteWebhook ok={}", response != null ? response.get("ok") : null);
        return response == null ? Map.of() : response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getWebhookInfo() {
        ensureConfigured();

        Map<String, Object> response = restClient.get()
                .uri(methodUri("getWebhookInfo"))
                .retrieve()
                .body(Map.class);

        return response == null ? Map.of() : response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMe() {
        ensureConfigured();

        Map<String, Object> response = restClient.get()
                .uri(methodUri("getMe"))
                .retrieve()
                .body(Map.class);

        return response == null ? Map.of() : response;
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Telegram bot is not configured. Set TELEGRAM_BOT_TOKEN as '<botId>:<secret>'"
            );
        }
    }

    private URI methodUri(String method) {
        String token = properties.botToken().trim();
        return UriComponentsBuilder
                .fromUriString(trimTrailingSlash(properties.apiUrl()))
                .path("/bot{token}/" + method)
                .buildAndExpand(token)
                .encode()
                .toUri();
    }

    private static String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "https://api.telegram.org";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
