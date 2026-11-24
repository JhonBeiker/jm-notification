package com.jmcode.notification.company;

import com.jayway.jsonpath.JsonPath;
import com.jmcode.notification.security.ApiKeyAuthFilter;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de la multi-empresa: el SUPER_ADMIN da de alta empresas y sus administradores,
 * y cada administrador sólo ve y toca los datos de la suya.
 *
 * <p>El alta común es estática porque JUnit instancia la clase por test y la base de datos
 * en memoria se comparte: repetirla chocaría con el código de empresa ya existente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "notification.email.enabled=true",
        "notification.telegram.enabled=false",
        "notification.auth.admin-email=super@jmcode.local",
        "notification.auth.admin-password=TestPassword!2026",
        "notification.auth.jwt-secret=company-scope-test-secret-32-chars",
        "notification.email.password-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLWFlcy1nY20hISE=",
        "spring.datasource.url=jdbc:h2:mem:jm-notification-company;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CompanyScopeIntegrationTest {

    private static final String ADMIN_A_EMAIL = "admin-a@empresa.local";
    private static final String ADMIN_A_PASSWORD = "AdminEmpresaA!26";

    private static boolean seeded;
    private static long companyAId;
    private static long companyBId;
    private static long accountBId;

    @Autowired
    private MockMvc mockMvc;

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        superToken = login("super@jmcode.local", "TestPassword!2026");
        if (!seeded) {
            companyAId = createCompany("empresa-a", "Empresa A");
            companyBId = createCompany("empresa-b", "Empresa B");
            createAdmin(ADMIN_A_EMAIL, ADMIN_A_PASSWORD, companyAId);
            accountBId = createEmailAccount(superToken, "cliente-b", companyBId, false);
            seeded = true;
        }
    }

    @Test
    @DisplayName("el login del administrador de empresa devuelve su empresa")
    void companyAdminLoginCarriesItsCompany() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN_A_EMAIL + "\",\"password\":\"" + ADMIN_A_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.companyId").value(companyAId))
                .andExpect(jsonPath("$.companyCode").value("empresa-a"));
    }

    @Test
    @DisplayName("la cuenta creada por el administrador se asigna a su empresa aunque no la envie")
    void createdAccountBelongsToTheAdminCompany() throws Exception {
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/email-accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailAccountBody("cliente-a-propia", null, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(companyAId))
                .andExpect(jsonPath("$.companyCode").value("empresa-a"));
    }

    @Test
    @DisplayName("el listado solo trae las cuentas de la empresa del administrador")
    void listOnlyShowsOwnCompanyAccounts() throws Exception {
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);
        createEmailAccount(token, "cliente-a-listado", null, false);

        mockMvc.perform(get("/api/v1/admin/email-accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.clientCode == 'cliente-b')]").isEmpty())
                .andExpect(jsonPath("$[?(@.clientCode == 'cliente-a-listado')]").isNotEmpty());
    }

    @Test
    @DisplayName("leer la cuenta de otra empresa devuelve 403")
    void readingAnotherCompanyAccountIsForbidden() throws Exception {
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/email-accounts/" + accountBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("asignar una cuenta a otra empresa devuelve 403")
    void assigningToAnotherCompanyIsForbidden() throws Exception {
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/email-accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailAccountBody("cliente-robado", companyBId, false)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la cuenta por defecto es global: solo la marca el SUPER_ADMIN")
    void onlySuperAdminSetsTheGlobalDefaultAccount() throws Exception {
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/email-accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailAccountBody("cliente-a-default", null, true)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la plantilla creada por el administrador queda en su empresa")
    void templatesAreScopedToTheCompany() throws Exception {
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/email-templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bienvenida\",\"subject\":\"Hola {{nombre}}\","
                                + "\"content\":\"<p>Hola {{nombre}}</p>\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(companyAId))
                // Sin contentType explicito se deduce del contenido.
                .andExpect(jsonPath("$.contentType").value("html"));
    }

    @Test
    @DisplayName("una API Key no puede enviar por el clientCode de otra empresa")
    void apiKeyCannotSendThroughAnotherCompanyAccount() throws Exception {
        String apiKey = createApiKey("api-empresa-a", companyAId);

        mockMvc.perform(post("/api/v1/notifications")
                        .header(ApiKeyAuthFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"to\":\"destino@example.com\","
                                + "\"message\":\"hola\",\"clientCode\":\"cliente-b\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("en el envío masivo, el clientCode ajeno sólo tumba ese elemento")
    void bulkMarksTheForbiddenItemAsFailed() throws Exception {
        String apiKey = createApiKey("api-empresa-a-bulk", companyAId);

        mockMvc.perform(post("/api/v1/notifications/bulk")
                        .header(ApiKeyAuthFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifications\":[{\"channel\":\"EMAIL\",\"to\":\"destino@example.com\","
                                + "\"message\":\"hola\",\"clientCode\":\"cliente-b\"}]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }

    @Test
    @DisplayName("el SUPER_ADMIN no puede crear una API Key sin empresa")
    void apiKeyWithoutCompanyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/api-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sin-empresa\",\"contactEmail\":\"dev@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el administrador crea su propia API Key sin indicar empresa")
    void companyAdminCreatesItsOwnApiKey() throws Exception {
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/api-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"api-propia\",\"contactEmail\":\"dev@empresa-a.local\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.companyId").value(companyAId))
                .andExpect(jsonPath("$.apiKey").isNotEmpty());
    }

    @Test
    @DisplayName("el listado de API Keys sólo trae las de su empresa, aunque pida otra")
    void apiKeyListIsScopedEvenWhenAskingForAnotherCompany() throws Exception {
        createApiKey("api-empresa-b-listado", companyBId);
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);
        mockMvc.perform(post("/api/v1/admin/api-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"api-empresa-a-listado\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/api-clients?companyId=" + companyBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'api-empresa-b-listado')]").isEmpty())
                .andExpect(jsonPath("$[?(@.name == 'api-empresa-a-listado')]").isNotEmpty());
    }

    @Test
    @DisplayName("rotar la API Key de otra empresa devuelve 403")
    void rotatingAnotherCompanyApiKeyIsForbidden() throws Exception {
        long apiClientBId = createApiClientId("api-empresa-b-rotar", companyBId);
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(post("/api/v1/admin/api-clients/" + apiClientBId + "/rotate-key")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/api-clients/" + apiClientBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("el administrador de empresa no puede crear empresas ni usuarios")
    void companyAdminCannotReachGlobalEndpoints() throws Exception {
        String token = login(ADMIN_A_EMAIL, ADMIN_A_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/companies").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un ADMIN sin empresa es rechazado en el alta")
    void adminWithoutCompanyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"suelto@empresa.local\",\"password\":\"SinEmpresa!26\","
                                + "\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("borrar una empresa con datos devuelve 409")
    void deletingACompanyWithDataConflicts() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/companies/" + companyBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken))
                .andExpect(status().isConflict());
    }

    private String createApiKey(String name, long companyId) throws Exception {
        return JsonPath.read(createApiClient(name, companyId), "$.apiKey");
    }

    private long createApiClientId(String name, long companyId) throws Exception {
        return ((Number) JsonPath.read(createApiClient(name, companyId), "$.client.id")).longValue();
    }

    private String createApiClient(String name, long companyId) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/api-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"contactEmail\":\"dev@example.com\","
                                + "\"companyId\":" + companyId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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

    private long createEmailAccount(String token, String clientCode, Long companyId, boolean isDefault)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/email-accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailAccountBody(clientCode, companyId, isDefault)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String emailAccountBody(String clientCode, Long companyId, boolean isDefault) {
        // Jackson no rellena los boolean primitivos ausentes: el DTO los exige todos.
        return "{\"clientCode\":\"" + clientCode + "\","
                + (companyId == null ? "" : "\"companyId\":" + companyId + ",")
                + "\"host\":\"smtp.example.com\",\"port\":587,"
                + "\"username\":\"user@example.com\",\"password\":\"secret\","
                + "\"auth\":true,\"starttlsEnable\":true,\"starttlsRequired\":false,\"sslEnable\":false,"
                + "\"active\":true,\"isDefault\":" + isDefault + "}";
    }
}
