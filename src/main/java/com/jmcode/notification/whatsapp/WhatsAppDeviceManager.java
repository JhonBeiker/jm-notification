package com.jmcode.notification.whatsapp;

import com.jmcode.notification.config.NotificationProperties;
import com.jmcode.notification.email.PasswordEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cachea un {@link GowaClient} por dispositivo, indexado por {@code clientCode} y versionado
 * con el {@code updatedAt} de la fila, de forma que editarla reemplaza la entrada en vez de
 * dejar la anterior viva para siempre.
 *
 * <p>La URL y las credenciales de cada fila caen a las globales
 * ({@code notification.whatsapp.api-url} / {@code .basic-auth-user} / {@code .basic-auth-password})
 * cuando están vacías: así una única instancia GOWA sirve a todas las empresas sin repetir la
 * contraseña en cada fila.
 */
@Service
public class WhatsAppDeviceManager {

    private final Map<String, CachedClient> clientCache = new ConcurrentHashMap<>();
    private final RestClient.Builder restClientBuilder;
    private final PasswordEncryptor passwordEncryptor;
    private final NotificationProperties.WhatsApp properties;

    public WhatsAppDeviceManager(RestClient.Builder restClientBuilder,
                                 PasswordEncryptor passwordEncryptor,
                                 NotificationProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.passwordEncryptor = passwordEncryptor;
        this.properties = properties.whatsapp();
    }

    public GowaClient getClient(WhatsAppDevice device) {
        Instant version = device.getUpdatedAt();
        CachedClient cached = clientCache.compute(device.getClientCode(), (key, current) ->
                current != null && current.matches(version)
                        ? current
                        : new CachedClient(version, createClient(device)));
        return cached.client();
    }

    public void evict(String clientCode) {
        clientCache.remove(clientCode);
    }

    private GowaClient createClient(WhatsAppDevice device) {
        String apiUrl = StringUtils.hasText(device.getApiUrl())
                ? device.getApiUrl()
                : properties.apiUrl();
        String user = StringUtils.hasText(device.getBasicAuthUser())
                ? device.getBasicAuthUser()
                : properties.basicAuthUser();
        String password = StringUtils.hasText(device.getBasicAuthUser())
                ? passwordEncryptor.decrypt(device.getBasicAuthPassword())
                : properties.basicAuthPassword();
        // El builder viene de Spring Boot, así que hereda los timeouts de spring.http.clients.
        return new GowaClient(restClientBuilder.build(), apiUrl, user, password);
    }

    private record CachedClient(Instant version, GowaClient client) {
        boolean matches(Instant candidate) {
            return version != null && version.equals(candidate);
        }
    }
}
