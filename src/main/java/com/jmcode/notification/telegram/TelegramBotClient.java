package com.jmcode.notification.telegram;

import com.jmcode.notification.channel.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cliente de la Bot API para una cuenta concreta. No es un bean: lo construye
 * {@link TelegramBotAccountManager} por cuenta, con el {@link RestClient} de Spring Boot
 * (y por tanto con los timeouts de {@code spring.http.clients}).
 */
public class TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotClient.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^\\d+:[A-Za-z0-9_-]+$");
    private static final String DEFAULT_API_URL = "https://api.telegram.org";
    private static final String[] ALLOWED_UPDATES = {"message", "edited_message", "my_chat_member"};
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    /** Cliente con read-timeout largo: subir un fichero no cabe en el timeout de texto. */
    private final RestClient uploadRestClient;
    private final String botToken;
    private final String apiUrl;

    public TelegramBotClient(RestClient restClient, RestClient uploadRestClient, String botToken, String apiUrl) {
        this.restClient = restClient;
        this.uploadRestClient = uploadRestClient;
        this.botToken = botToken;
        this.apiUrl = apiUrl;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(botToken) && TOKEN_PATTERN.matcher(botToken.trim()).matches();
    }

    /** Mensaje auxiliar (bienvenida/despedida): nunca propaga el fallo. */
    public Map<String, Object> sendText(Long chatId, String text) {
        if (!isConfigured() || chatId == null || !StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            return post("sendMessage", Map.of("chat_id", chatId, "text", text));
        } catch (Exception ex) {
            log.warn("Failed to send Telegram helper message to {}: {}", chatId, ex.getMessage());
            return errorResponse(ex);
        }
    }

    public Map<String, Object> sendNotification(String chatId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "Markdown");
        return post("sendMessage", body);
    }

    public Map<String, Object> sendDocument(String chatId, String caption, List<Attachment> attachments) {
        if (!isConfigured() || chatId == null || attachments == null || attachments.isEmpty()) {
            return sendNotification(chatId, caption);
        }
        // sendDocument admite un único fichero por llamada: se envía el primero.
        Attachment attachment = attachments.get(0);
        if (attachment == null || !StringUtils.hasText(attachment.base64Content())) {
            return sendNotification(chatId, caption);
        }

        try {
            byte[] fileContent = Base64.getDecoder().decode(attachment.base64Content());
            String fileName = StringUtils.hasText(attachment.name()) ? attachment.name() : "document";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", chatId);
            if (StringUtils.hasText(caption)) {
                body.add("caption", caption);
                body.add("parse_mode", "Markdown");
            }
            body.add("document", new NamedByteArrayResource(fileContent, fileName));

            Map<String, Object> response = uploadRestClient.post()
                    .uri(methodUri("sendDocument"))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(RESPONSE_TYPE);
            return response == null ? Map.of() : response;
        } catch (IllegalArgumentException ex) {
            log.error("Attachment for chat {} is not valid Base64", chatId, ex);
            return errorResponse(ex);
        } catch (ResourceAccessException ex) {
            log.error("Telegram document upload to {} timed out or failed at transport level "
                    + "(size={} bytes). Raise notification.telegram.upload-read-timeout if the file is large.",
                    chatId, sizeOf(attachment), ex);
            return Map.of("ok", false, "description",
                    "Upload to Telegram timed out or was interrupted: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send Telegram document to {}", chatId, ex);
            return errorResponse(ex);
        }
    }

    public Map<String, Object> setWebhook(String webhookUrl, String secretToken) {
        ensureConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", webhookUrl);
        body.put("drop_pending_updates", true);
        body.put("allowed_updates", ALLOWED_UPDATES);
        if (StringUtils.hasText(secretToken)) {
            body.put("secret_token", secretToken);
        }
        Map<String, Object> response = post("setWebhook", body);
        log.info("Telegram setWebhook url={} ok={}", webhookUrl, response.get("ok"));
        return response;
    }

    public Map<String, Object> deleteWebhook() {
        ensureConfigured();
        Map<String, Object> response = post("deleteWebhook", Map.of("drop_pending_updates", true));
        log.info("Telegram deleteWebhook ok={}", response.get("ok"));
        return response;
    }

    public Map<String, Object> getWebhookInfo() {
        ensureConfigured();
        return get("getWebhookInfo");
    }

    public Map<String, Object> getMe() {
        ensureConfigured();
        return get("getMe");
    }

    private Map<String, Object> post(String method, Map<String, Object> body) {
        Map<String, Object> response = restClient.post()
                .uri(methodUri(method))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(RESPONSE_TYPE);
        return response == null ? Map.of() : response;
    }

    private Map<String, Object> get(String method) {
        Map<String, Object> response = restClient.get()
                .uri(methodUri(method))
                .retrieve()
                .body(RESPONSE_TYPE);
        return response == null ? Map.of() : response;
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Telegram bot token is missing or malformed. Expected '<botId>:<secret>' from @BotFather");
        }
    }

    private URI methodUri(String method) {
        return UriComponentsBuilder
                .fromUriString(trimTrailingSlash(apiUrl))
                .path("/bot{token}/" + method)
                .buildAndExpand(botToken.trim())
                .encode()
                .toUri();
    }

    /** Tamaño aproximado del binario decodificado, para diagnosticar timeouts de subida. */
    private static long sizeOf(Attachment attachment) {
        String content = attachment.base64Content();
        return content == null ? 0L : (long) (content.length() * 3L / 4L);
    }

    private static Map<String, Object> errorResponse(Exception ex) {
        return Map.of("ok", false, "description", String.valueOf(ex.getMessage()));
    }

    private static String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return DEFAULT_API_URL;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** {@link ByteArrayResource} con nombre de fichero, requerido por el multipart de Telegram. */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
