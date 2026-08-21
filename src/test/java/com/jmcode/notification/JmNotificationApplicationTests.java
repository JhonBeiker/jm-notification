package com.jmcode.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "notification.email.enabled=false",
        "notification.telegram.enabled=false",
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "spring.datasource.url=jdbc:h2:mem:jm-notification-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class JmNotificationApplicationTests {

    @Test
    void contextLoads() {
    }
}
