package com.jmcode.notification.whatsapp;

import com.jmcode.notification.common.UpstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de <a href="https://github.com/aldinokemal/go-whatsapp-web-multidevice">GOWA</a>
 * para un dispositivo concreto. No es un bean: lo construye {@link WhatsAppDeviceManager} por
 * fila, con el {@link RestClient} de Spring Boot (y por tanto con los timeouts de
 * {@code spring.http.clients}).
 *
 * <p>La instancia se protege con Basic Auth global ({@code APP_BASIC_AUTH}) y el dispositivo se
 * elige con la cabecera {@code X-Device-Id}. Las respuestas vienen envueltas en
 * {@code {code, message, results}}: aquí se devuelve ya sólo {@code results}.
 */
public class GowaClient {

    private static final Logger log = LoggerFactory.getLogger(GowaClient.class);
    private static final String PROVIDER = "gowa";
    private static final String DEVICE_HEADER = "X-Device-Id";
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String apiUrl;
    private final String basicAuthHeader;

    public GowaClient(RestClient restClient, String apiUrl, String user, String password) {
        this.restClient = restClient;
        this.apiUrl = trimTrailingSlash(apiUrl);
        this.basicAuthHeader = basicAuth(user, password);
    }

    /** El token de Basic Auth es opcional: una instancia sin {@code APP_BASIC_AUTH} no lo pide. */
    public boolean isConfigured() {
        return StringUtils.hasText(apiUrl);
    }

    // --- Dispositivos (ámbito de la instancia, sin X-Device-Id) ---

    /** Alta de un dispositivo. GOWA genera el id (UUID); es el que se guarda en la fila. */
    public Map<String, Object> createDevice() {
        return asMap(exchange(HttpMethod.POST, uri("/devices"), null, Map.of()));
    }

    public List<Map<String, Object>> listDevices() {
        Object results = exchange(HttpMethod.GET, uri("/devices"), null, null);
        if (results instanceof List<?> raw) {
            return raw.stream().filter(Map.class::isInstance).map(GowaClient::castMap).toList();
        }
        return List.of();
    }

    public Map<String, Object> getDevice(String deviceId) {
        return asMap(exchange(HttpMethod.GET, uri("/devices/" + deviceId), null, null));
    }

    /** Borra el dispositivo en GOWA. Deshace el emparejamiento: hay que volver a escanear. */
    public Map<String, Object> deleteDevice(String deviceId) {
        return asMap(exchange(HttpMethod.DELETE, uri("/devices/" + deviceId), null, null));
    }

    // --- Emparejamiento y envío (por dispositivo, con X-Device-Id) ---

    /**
     * Pide el QR. {@code results.qr_link} es una URL a un PNG que sirve la propia instancia
     * (sin Basic Auth) y que caduca en {@code qr_duration} segundos.
     */
    public Map<String, Object> login(String deviceId) {
        return asMap(exchange(HttpMethod.GET, uri("/app/login"), deviceId, null));
    }

    /** Emparejamiento por código: devuelve {@code results.pair_code} para teclear en el móvil. */
    public Map<String, Object> loginWithCode(String deviceId, String phoneNumber) {
        URI uri = UriComponentsBuilder.fromUriString(apiUrl + "/app/login-with-code")
                .queryParam("phone", phoneNumber)
                .build()
                .encode()
                .toUri();
        return asMap(exchange(HttpMethod.GET, uri, deviceId, null));
    }

    /** Reabre la sesión de un dispositivo ya emparejado, sin volver a pedir QR. */
    public Map<String, Object> reconnect(String deviceId) {
        return asMap(exchange(HttpMethod.GET, uri("/app/reconnect"), deviceId, null));
    }

    /** Cierra la sesión de WhatsApp; el dispositivo sigue existiendo en GOWA. */
    public Map<String, Object> logout(String deviceId) {
        return asMap(exchange(HttpMethod.GET, uri("/app/logout"), deviceId, null));
    }

    public Map<String, Object> sendText(String deviceId, String phone, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("message", message);
        return asMap(exchange(HttpMethod.POST, uri("/send/message"), deviceId, body));
    }

    /**
     * Ejecuta la llamada y devuelve el {@code results} desenvuelto. Un error HTTP se traduce a
     * {@link UpstreamServiceException} (502) con el {@code code}/{@code message} de GOWA, que es
     * lo único que permite distinguir "no estás logueado" de un fallo real de la instancia.
     */
    private Object exchange(HttpMethod method, URI uri, String deviceId, Map<String, Object> body) {
        ensureConfigured();
        try {
            RestClient.RequestBodySpec spec = restClient.method(method).uri(uri);
            if (StringUtils.hasText(basicAuthHeader)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, basicAuthHeader);
            }
            if (StringUtils.hasText(deviceId)) {
                spec = spec.header(DEVICE_HEADER, deviceId);
            }
            if (body != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON);
                spec.body(body);
            }
            Map<String, Object> response = spec.retrieve().body(RESPONSE_TYPE);
            return response == null ? Map.of() : response.getOrDefault("results", Map.of());
        } catch (RestClientResponseException ex) {
            String detail = describe(ex);
            // Un 4xx suele ser un estado del dispositivo (sin login, id desconocido) más que
            // una avería: quien no lo tolere ya lo propaga como 502.
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("GOWA {} {} responded {}: {}", method, uri.getPath(), ex.getStatusCode(), detail);
            } else {
                log.error("GOWA {} {} failed with {}: {}", method, uri.getPath(), ex.getStatusCode(), detail);
            }
            throw new UpstreamServiceException(PROVIDER, ex.getStatusCode().value(),
                    "GOWA responded " + ex.getStatusCode().value() + ": " + detail, ex);
        } catch (ResourceAccessException ex) {
            log.error("GOWA {} {} unreachable", method, uri.getPath(), ex);
            throw new UpstreamServiceException(PROVIDER, 0,
                    "GOWA server is unreachable at " + apiUrl + ": " + ex.getMessage(), ex);
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "GOWA is not configured: set the device apiUrl or the global "
                            + "notification.whatsapp.api-url");
        }
    }

    private URI uri(String path) {
        return URI.create(apiUrl + path);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> asMap(Object results) {
        return results instanceof Map<?, ?> map ? castMap(map) : Map.of();
    }

    private static String basicAuth(String user, String password) {
        if (!StringUtils.hasText(user)) {
            return "";
        }
        String raw = user.trim() + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String describe(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return StringUtils.hasText(body) ? body : ex.getStatusText();
    }

    private static String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
