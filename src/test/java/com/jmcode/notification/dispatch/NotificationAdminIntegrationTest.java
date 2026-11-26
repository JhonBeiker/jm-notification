package com.jmcode.notification.dispatch;

import com.jayway.jsonpath.JsonPath;
import com.jmcode.notification.security.filter.ApiKeyAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "notification.email.enabled=false",
        "notification.telegram.enabled=false",
        "notification.whatsapp.enabled=false",
        "notification.auth.admin-email=super@jmcode.local",
        "notification.auth.admin-password=TestPassword!2026",
        "notification.auth.jwt-secret=notifications-list-test-secret-32c",
        "notification.email.password-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLWFlcy1nY20hISE=",
        "spring.datasource.url=jdbc:h2:mem:jm-notification-list;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationAdminIntegrationTest {

    private static final String ADMIN_A_EMAIL = "admin-a@notif.local";
    private static final String ADMIN_A_PASSWORD = "AdminNotifA!26";

    private static boolean seeded;
    private static long companyAId;
    private static long companyBId;
    private static String apiKeyA;
    private static String apiKeyB;

    @Autowired
    private MockMvc mockMvc;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        superToken = login("super@jmcode.local", "TestPassword!2026");
        if (!seeded) {
            companyAId = createCompany("notif-a", "Notif A");
            companyBId = createCompany("notif-b", "Notif B");
            createAdmin(ADMIN_A_EMAIL, ADMIN_A_PASSWORD, companyAId);
            apiKeyA = createApiKey("api-notif-a", companyAId);
            apiKeyB = createApiKey("api-notif-b", companyBId);

            sendNotification(apiKeyA, "a1@example.com", "Asunto A1");
            sendNotification(apiKeyA, "a2@example.com", "Asunto A2");
            sendNotification(apiKeyA, "a3@example.com", "Asunto A3");
            sendNotification(apiKeyB, "b1@example.com", "Asunto B1");
            sendNotification(apiKeyB, "b2@example.com", "Asunto B2");
            seeded = true;
        }
    }

    @Test
    @DisplayName("exige autenticación JWT de administrador")
    void requiresAdminAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/notifications")
                        .header(ApiKeyAuthFilter.HEADER, apiKeyA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("el SUPER_ADMIN lista todas las notificaciones paginadas de más reciente a más antigua")
    void superAdminListsPaginatedNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].to").value("b2@example.com"))
                .andExpect(jsonPath("$.content[1].to").value("b1@example.com"));

        mockMvc.perform(get("/api/v1/admin/notifications?page=2&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].to").value("a1@example.com"));
    }

    @Test
    @DisplayName("el SUPER_ADMIN puede filtrar por empresa con ?companyId=")
    void superAdminFiltersByCompanyId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications?companyId=" + companyBId + "&page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].companyId").value(companyBId))
                .andExpect(jsonPath("$.content[0].companyCode").value("notif-b"))
                .andExpect(jsonPath("$.content[1].companyId").value(companyBId));
    }

    @Test
    @DisplayName("el ADMIN sólo ve las notificaciones de su empresa aunque pida otra")
    void companyAdminIsScopedToOwnCompany() throws Exception {
        String adminAToken = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/notifications?companyId=" + companyBId + "&page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].companyId").value(companyAId))
                .andExpect(jsonPath("$.content[0].companyCode").value("notif-a"))
                .andExpect(jsonPath("$.content[?(@.companyId == " + companyBId + ")]").isEmpty());
    }

    @Test
    @DisplayName("parámetros de paginación inválidos devuelven 400")
    void invalidPaginationReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications?page=-1&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/notifications?page=0&size=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/notifications?page=0&size=101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());
    }

    private void sendNotification(String apiKey, String to, String subject) throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header(ApiKeyAuthFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"to\":\"" + to + "\",\"subject\":\"" + subject
                                + "\",\"message\":\"Hola\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long createCompany(String code, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void createAdmin(String email, String password, long companyId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\","
                                + "\"role\":\"ADMIN\",\"companyId\":" + companyId + "}"))
                .andExpect(status().isCreated());
    }

    private String createApiKey(String name, long companyId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/api-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"contactEmail\":\"dev@example.com\","
                                + "\"companyId\":" + companyId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.apiKey");
    }
}
