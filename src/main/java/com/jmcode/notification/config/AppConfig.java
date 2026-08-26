package com.jmcode.notification.config;

import com.jmcode.notification.security.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * No se declara aquí ningún {@code RestClient.Builder} ni {@code ObjectMapper}:
 * ambos los autoconfigura Spring Boot. Declararlos a mano desactivaba la
 * autoconfiguración (los beans de Boot son {@code @ConditionalOnMissingBean}) y
 * con ella los timeouts de {@code spring.http.clients} y los módulos Jackson.
 */
@Configuration
@EnableConfigurationProperties({NotificationProperties.class, AuthProperties.class})
public class AppConfig {
}
