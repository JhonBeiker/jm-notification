package com.jmcode.notification.dispatch;

import com.jayway.jsonpath.JsonPath;
import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.company.entity.Company;
import com.jmcode.notification.company.repository.CompanyRepository;
import com.jmcode.notification.dispatch.entity.NotificationLog;
import com.jmcode.notification.dispatch.repository.NotificationLogRepository;
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

import java.time.Instant;

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

    private static final Instant T_OLD = Instant.parse("2026-01-10T10:00:00Z");
    private static final Instant T_MID = Instant.parse("2026-06-15T12:00:00Z");

    private static boolean seeded;
    private static long companyAId;
    private static long companyBId;
    private static String apiKeyA;
    private static String apiKeyB;
    private static long notifAId;
    private static long notifBId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

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

            Company companyA = companyRepository.findById(companyAId).orElseThrow();
            Company companyB = companyRepository.findById(companyBId).orElseThrow();

            // 2 registros con fechas/estados/canales controlados para probar filtros y stats
            notifAId = saveLog(companyA, ChannelType.TELEGRAM, NotificationStatus.SENT,
                    "user-tg-1", "Alerta TG", "bot-a", T_OLD).getId();
            notifBId = saveLog(companyB, ChannelType.WHATSAPP, NotificationStatus.FAILED,
                    "+584261112233", "Alerta WA", "wa-b", T_MID).getId();

            // 3 registros vía API real (quedan SKIPPED con timestamp actual)
            sendNotification(apiKeyA, "a1@example.com", "Asunto A1", "smtp-a");
            sendNotification(apiKeyA, "a2@example.com", "Asunto A2", "smtp-a");
            sendNotification(apiKeyB, "b1@example.com", "Asunto B1", "smtp-b");
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
                .andExpect(jsonPath("$.content[0].to").value("b1@example.com"))
                .andExpect(jsonPath("$.content[1].to").value("a2@example.com"));

        mockMvc.perform(get("/api/v1/admin/notifications?page=2&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].to").value("user-tg-1"));
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
    @DisplayName("filtra por canal, estado, destinatario parcial, clientCode y rango de fechas")
    void filtersByChannelStatusRecipientClientCodeAndDates() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications?channel=TELEGRAM&status=SENT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(notifAId));

        mockMvc.perform(get("/api/v1/admin/notifications?recipient=EXAMPLE.COM&clientCode=SMTP-A")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].clientCode").value("smtp-a"));

        mockMvc.perform(get("/api/v1/admin/notifications?from=2026-01-01T00:00:00Z&until=2026-07-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(notifBId))
                .andExpect(jsonPath("$.content[1].id").value(notifAId));
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
    @DisplayName("GET /{id} devuelve el detalle respetando el aislamiento por empresa y 404 si no existe")
    void getByIdRespectsCompanyScopeAndNotFound() throws Exception {
        String adminAToken = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/notifications/" + notifAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notifAId))
                .andExpect(jsonPath("$.channel").value("TELEGRAM"))
                .andExpect(jsonPath("$.status").value("SENT"));

        mockMvc.perform(get("/api/v1/admin/notifications/" + notifBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/notifications/" + notifBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notifBId));

        mockMvc.perform(get("/api/v1/admin/notifications/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /stats devuelve totales por estado y canal respetando empresa y fechas")
    void statsAggregatesByStatusAndChannel() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.byStatus.SENT").value(1))
                .andExpect(jsonPath("$.byStatus.FAILED").value(1))
                .andExpect(jsonPath("$.byStatus.SKIPPED").value(3))
                .andExpect(jsonPath("$.byChannel.EMAIL").value(3))
                .andExpect(jsonPath("$.byChannel.TELEGRAM").value(1))
                .andExpect(jsonPath("$.byChannel.WHATSAPP").value(1));

        String adminAToken = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);
        mockMvc.perform(get("/api/v1/admin/notifications/stats?companyId=" + companyBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.byStatus.SENT").value(1))
                .andExpect(jsonPath("$.byStatus.FAILED").value(0))
                .andExpect(jsonPath("$.byStatus.SKIPPED").value(2))
                .andExpect(jsonPath("$.byChannel.EMAIL").value(2))
                .andExpect(jsonPath("$.byChannel.TELEGRAM").value(1))
                .andExpect(jsonPath("$.byChannel.WHATSAPP").value(0));

        mockMvc.perform(get("/api/v1/admin/notifications/stats?from=2026-06-01T00:00:00Z&until=2026-07-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byStatus.FAILED").value(1))
                .andExpect(jsonPath("$.byChannel.WHATSAPP").value(1));
    }

    @Test
    @DisplayName("parámetros de paginación, rango de fechas o tipos inválidos devuelven 400")
    void invalidParamsReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications?page=-1&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/notifications?page=0&size=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/notifications?page=0&size=101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/notifications?from=2026-07-01T00:00:00Z&until=2026-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/notifications?channel=INVALID")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/notifications/stats?from=not-a-date")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isBadRequest());
    }

    private NotificationLog saveLog(Company company, ChannelType channel, NotificationStatus status,
                                    String recipient, String subject, String clientCode, Instant createdAt) {
        NotificationLog log = new NotificationLog();
        log.setCompany(company);
        log.setChannel(channel);
        log.setStatus(status);
        log.setRecipient(recipient);
        log.setSubject(subject);
        log.setClientCode(clientCode);
        log.setCreatedAt(createdAt);
        return notificationLogRepository.save(log);
    }

    private void sendNotification(String apiKey, String to, String subject, String clientCode) throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header(ApiKeyAuthFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"to\":\"" + to + "\",\"subject\":\"" + subject
                                + "\",\"clientCode\":\"" + clientCode + "\",\"message\":\"Hola\"}"))
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
