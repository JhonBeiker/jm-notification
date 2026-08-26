package com.jmcode.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Arranca el contexto completo contra H2 en memoria. Antes fallaba porque
 * {@code application.yml} fijaba el driver de Postgres y este test apunta a H2.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "notification.email.enabled=false",
        "notification.telegram.enabled=false",
        "notification.auth.admin-email=test-admin@jmcode.local",
        "notification.auth.admin-password=TestPassword!2026",
        "notification.auth.jwt-secret=test-jwt-secret-with-at-least-32-chars",
        "notification.email.password-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLWFlcy1nY20hISE=",
        "spring.datasource.url=jdbc:h2:mem:jm-notification-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class JmNotificationApplicationTests {

    @Test
    void contextLoads() {
    }
}
