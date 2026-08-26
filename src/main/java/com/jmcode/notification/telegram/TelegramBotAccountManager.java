package com.jmcode.notification.telegram;

import com.jmcode.notification.config.NotificationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cachea un {@link TelegramBotClient} por cuenta, indexado por {@code clientCode} y
 * versionado con el {@code updatedAt} de la cuenta, de forma que editarla reemplaza la
 * entrada en vez de dejar la anterior viva para siempre.
 */
@Service
public class TelegramBotAccountManager {

    private final Map<String, CachedClient> clientCache = new ConcurrentHashMap<>();
    private final RestClient.Builder restClientBuilder;
    private final RestClient uploadRestClient;

    public TelegramBotAccountManager(RestClient.Builder restClientBuilder, NotificationProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.uploadRestClient = buildUploadClient(restClientBuilder, properties.telegram());
    }

    public TelegramBotClient getClient(TelegramBotAccount account) {
        Instant version = account.getUpdatedAt();
        CachedClient cached = clientCache.compute(account.getClientCode(), (key, current) ->
                current != null && current.matches(version)
                        ? current
                        : new CachedClient(version, createClient(account)));
        return cached.client();
    }

    public void evict(String clientCode) {
        clientCache.remove(clientCode);
    }

    private TelegramBotClient createClient(TelegramBotAccount account) {
        // El builder viene de Spring Boot, así que hereda los timeouts de spring.http.clients.
        return new TelegramBotClient(restClientBuilder.build(), uploadRestClient,
                account.getBotToken(), account.getApiUrl());
    }

    /**
     * Cliente aparte para {@code sendDocument}: subir un fichero no cabe en el read-timeout
     * general (pensado para llamadas de texto), y con él las subidas se cancelaban a mitad.
     */
    private static RestClient buildUploadClient(RestClient.Builder builder, NotificationProperties.Telegram telegram) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(telegram.uploadConnectTimeout())
                .withReadTimeout(telegram.uploadReadTimeout());
        return builder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    private record CachedClient(Instant version, TelegramBotClient client) {
        boolean matches(Instant candidate) {
            return version != null && version.equals(candidate);
        }
    }
}
