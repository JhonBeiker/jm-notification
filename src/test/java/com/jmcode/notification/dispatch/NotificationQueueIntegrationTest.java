package com.jmcode.notification.dispatch;

import com.jayway.jsonpath.JsonPath;
import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.company.entity.Company;
import com.jmcode.notification.company.repository.CompanyRepository;
import com.jmcode.notification.dispatch.entity.NotificationLog;
import com.jmcode.notification.dispatch.repository.NotificationLogRepository;
import com.jmcode.notification.dispatch.service.NotificationQueueService;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        "notification.queue.worker-enabled=false",
        "notification.queue.max-attempts=3",
        "notification.auth.admin-email=super@queue.local",
        "notification.auth.admin-password=TestPassword!2026",
        "notification.auth.jwt-secret=notifications-queue-test-secret-32",
        "notification.email.password-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLWFlcy1nY20hISE=",
        "spring.datasource.url=jdbc:h2:mem:jm-notification-queue;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationQueueIntegrationTest {

    private static final String ADMIN_A_EMAIL = "admin-a@queue.local";
    private static final String ADMIN_A_PASSWORD = "AdminQueueA!26";

    private static boolean seeded;
    private static long companyAId;
    private static long companyBId;
    private static String apiKeyA;
    private static String apiKeyB;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationQueueService queueService;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        superToken = login("super@queue.local", "TestPassword!2026");
        if (!seeded) {
            companyAId = createCompany("queue-a", "Queue A");
            companyBId = createCompany("queue-b", "Queue B");
            createAdmin(ADMIN_A_EMAIL, ADMIN_A_PASSWORD, companyAId);
            apiKeyA = createApiKey("api-queue-a", companyAId);
            apiKeyB = createApiKey("api-queue-b", companyBId);
            seeded = true;
        }
    }

    @Test
    @DisplayName("POST /notifications?async=true encola, permite consultar estado con API Key y el worker lo procesa")
    void enqueuePollAndProcessAsyncNotification() throws Exception {
        String response = mockMvc.perform(post("/api/v1/notifications?async=true")
                        .header(ApiKeyAuthFilter.HEADER, apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","to":"async@example.com","subject":"Async","message":"Hola"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(response, "$.id")).longValue();

        // La propia API Key consulta su estado
        mockMvc.perform(get("/api/v1/notifications/" + id)
                        .header(ApiKeyAuthFilter.HEADER, apiKeyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.maxAttempts").value(3));

        // Otra empresa no puede ver el estado de esta notificación
        mockMvc.perform(get("/api/v1/notifications/" + id)
                        .header(ApiKeyAuthFilter.HEADER, apiKeyB))
                .andExpect(status().isForbidden());

        // El worker drena la cola (canal deshabilitado -> pasa a SKIPPED con attempts=1)
        int processed = queueService.processPendingBatch();
        assertEquals(1, processed);

        mockMvc.perform(get("/api/v1/notifications/" + id)
                        .header(ApiKeyAuthFilter.HEADER, apiKeyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.nextAttemptAt").doesNotExist());
    }

    @Test
    @DisplayName("POST /notifications/bulk?async=true encola todo el lote")
    void bulkAsyncEnqueuesAllItems() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/bulk?async=true")
                        .header(ApiKeyAuthFilter.HEADER, apiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notifications":[
                                  {"channel":"EMAIL","to":"b1@example.com","subject":"S1","message":"M1"},
                                  {"channel":"TELEGRAM","to":"12345","subject":"S2","message":"M2"}
                                ]}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].status").value("QUEUED"))
                .andExpect(jsonPath("$[1].id").isNumber())
                .andExpect(jsonPath("$[1].status").value("QUEUED"));

        queueService.processPendingBatch();
    }

    @Test
    @DisplayName("POST /admin/notifications/{id}/retry reencola FAILED, bloquea entre empresas (403) y rechaza estados inválidos (409)")
    void adminRetryRespectsCompanyScopeAndStatus() throws Exception {
        Company companyA = companyRepository.findById(companyAId).orElseThrow();
        Company companyB = companyRepository.findById(companyBId).orElseThrow();

        NotificationLog failedA = saveFailedLog(companyA, true);
        NotificationLog failedB = saveFailedLog(companyB, true);
        NotificationLog failedNoPayload = saveFailedLog(companyA, false);

        String adminAToken = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        // ADMIN A reintenta la suya -> 202 QUEUED
        mockMvc.perform(post("/api/v1/admin/notifications/" + failedA.getId() + "/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(failedA.getId()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.maxAttempts").value(4));

        // Volver a reintentar mientras está QUEUED -> 409
        mockMvc.perform(post("/api/v1/admin/notifications/" + failedA.getId() + "/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAToken))
                .andExpect(status().isConflict());

        // ADMIN A intenta reintentar una de empresa B -> 403
        mockMvc.perform(post("/api/v1/admin/notifications/" + failedB.getId() + "/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAToken))
                .andExpect(status().isForbidden());

        // FAILED sin payload almacenado -> 409
        mockMvc.perform(post("/api/v1/admin/notifications/" + failedNoPayload.getId() + "/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isConflict());

        // Inexistente -> 404
        mockMvc.perform(post("/api/v1/admin/notifications/999999/retry")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isNotFound());

        queueService.processPendingBatch();
    }

    private NotificationLog saveFailedLog(Company company, boolean withPayload) {
        NotificationLog entry = new NotificationLog();
        entry.setCompany(company);
        entry.setChannel(ChannelType.EMAIL);
        entry.setStatus(NotificationStatus.FAILED);
        entry.setRecipient("retry@example.com");
        entry.setSubject("Retry");
        entry.setDetail("provider timeout");
        entry.setAttempts(3);
        entry.setMaxAttempts(3);
        entry.setCreatedAt(Instant.now());
        if (withPayload) {
            NotificationRequest req = new NotificationRequest(
                    ChannelType.EMAIL, "retry@example.com", "Retry", "Body", null, Map.of(), null, Map.of(), List.of()
            );
            entry.setPayloadJson(objectMapper.writeValueAsString(req));
        }
        return notificationLogRepository.save(entry);
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
