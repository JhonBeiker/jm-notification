package com.jmcode.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        @DefaultValue Email email,
        @DefaultValue WhatsApp whatsapp,
        @DefaultValue Telegram telegram
) {

    public record Email(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("") String from,
            /** Clave AES en Base64 (16/24/32 bytes) para cifrar passwords SMTP en BD. */
            @DefaultValue("") String passwordEncryptionKey
    ) {
    }

    /**
     * Instancia GOWA (go-whatsapp-web-multidevice) por defecto. Cada fila
     * {@code whatsapp_devices} puede traer su propia URL y sus credenciales; cuando no lo
     * hace, usa estas.
     */
    public record WhatsApp(
            @DefaultValue("false") boolean enabled,
            /** Base URL de la instancia GOWA, sin path. */
            @DefaultValue("") String apiUrl,
            /** Usuario de Basic Auth ({@code APP_BASIC_AUTH}); vacío si la instancia no la exige. */
            @DefaultValue("") String basicAuthUser,
            @DefaultValue("") String basicAuthPassword
    ) {
    }

    public record Telegram(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("https://api.telegram.org") String apiUrl,
            @DefaultValue("") String webhookSecret,
            @DefaultValue("true") boolean requireActiveSubscriber,
            @DefaultValue("") String welcomeMessage,
            @DefaultValue("") String goodbyeMessage,
            @DefaultValue("") String webhookUrl,
            @DefaultValue("false") boolean webhookAutoRegister,
            /**
             * Timeout de lectura para {@code sendDocument}. Subir un adjunto tarda mucho más
             * que enviar texto, así que no puede compartir el read-timeout general de
             * {@code spring.http.clients} (con 15s fallaba con "Request cancelled").
             */
            @DefaultValue("2m") Duration uploadReadTimeout,
            @DefaultValue("10s") Duration uploadConnectTimeout
    ) {
    }
}
