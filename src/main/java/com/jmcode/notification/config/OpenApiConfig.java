package com.jmcode.notification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "adminJwt";
    private static final String API_KEY_SCHEME = "apiKey";

    /**
     * No se declara ningún servidor fijo: springdoc usa la URL desde la que se sirve la
     * documentación (antes estaba clavado a {@code localhost:8080}, un puerto que ni
     * siquiera es el de esta aplicación).
     */
    @Bean
    OpenAPI notificationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("JM Notification API")
                        .description("""
                                API multi-canal para envío de notificaciones por Email, WhatsApp y Telegram.

                                Autenticación:
                                - Endpoints `/api/v1/admin/**`: JWT de administrador (`Authorization: Bearer ...`),
                                  obtenido en `POST /api/v1/admin/auth/login`.
                                - Endpoints `/api/v1/notifications/**`: API Key de cliente (`X-Api-Key`).
                                """)
                        .version("v1")
                        .contact(new Contact().name("JMCODE").email("dev@jmcode.com"))
                        .license(new License().name("Proprietary")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT devuelto por /api/v1/admin/auth/login"))
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")
                                .description("API Key de cliente, creada en /api/v1/admin/api-clients")));
    }
}
