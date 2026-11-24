package com.jmcode.notification.security;

import com.jmcode.notification.company.Company;
import com.jmcode.notification.company.CompanyRepository;
import com.jmcode.notification.telegram.TelegramBotAccount;
import com.jmcode.notification.telegram.TelegramBotAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fija el contrato HTTP que cambió al endurecer la seguridad: credenciales inválidas
 * devuelven 401 (no 404), los endpoints no enumerados exigen autenticación, y el webhook
 * de Telegram rechaza secrets desconocidos en lugar de caer a la cuenta por defecto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "notification.email.enabled=false",
        "notification.telegram.enabled=true",
        "notification.auth.admin-email=admin@jmcode.local",
        "notification.auth.admin-password=TestPassword!2026",
        "notification.auth.jwt-secret=integration-test-secret-of-32-chars",
        "notification.email.password-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLWFlcy1nY20hISE=",
        "spring.datasource.url=jdbc:h2:mem:jm-notification-contract;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SecurityContractIntegrationTest {

    private static final String WEBHOOK_SECRET = "secret-for-cliente-a";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TelegramBotAccountRepository botAccountRepository;

    @Autowired
    private ApiClientService apiClientService;

    @Autowired
    private CompanyRepository companyRepository;

    /** Toda API Key pertenece a una empresa: estos tests necesitan una a la que colgarlas. */
    private Long companyId;

    @BeforeEach
    void seedBotAccountWithSecret() {
        companyId = companyRepository.findByCodeIgnoreCase("contract-co")
                .orElseGet(() -> {
                    Company company = new Company();
                    company.setCode("contract-co");
                    company.setName("Contract Co");
                    return companyRepository.save(company);
                })
                .getId();
        if (botAccountRepository.count() == 0) {
            TelegramBotAccount account = new TelegramBotAccount();
            account.setClientCode("cliente-a");
            account.setBotToken("123456:AAbbCCddEEffGG_hh-ii");
            account.setApiUrl("https://api.telegram.org");
            account.setWebhookSecret(WEBHOOK_SECRET);
            account.setActive(true);
            account.setDefault(true);
            botAccountRepository.save(account);
        }
    }

    @Test
    @DisplayName("login con credenciales inválidas devuelve 401, no 404")
    void invalidLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@jmcode.local","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login de un usuario inexistente también devuelve 401")
    void unknownUserLoginReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@jmcode.local","password":"whatever"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login correcto devuelve un JWT")
    void validLoginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@jmcode.local","password":"TestPassword!2026"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
    }

    @Test
    @DisplayName("los endpoints de admin exigen autenticación")
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/email-accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("las notificaciones exigen API key")
    void notificationsRequireApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/channels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("una API key válida da acceso al estado de canales")
    void validApiKeyGrantsAccessToChannels() throws Exception {
        String apiKey = apiClientService.create("contract-test", "dev@jmcode.local", companyId).plainKey();

        mockMvc.perform(get("/api/v1/notifications/channels").header(ApiKeyAuthFilter.HEADER, apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.EMAIL").value(false))
                .andExpect(jsonPath("$.TELEGRAM").value(true));
    }

    @Test
    @DisplayName("una API key desconocida no autentica")
    void unknownApiKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/channels").header(ApiKeyAuthFilter.HEADER, "jmk_not-a-real-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el webhook de Telegram rechaza peticiones sin secret cuando alguna cuenta lo exige")
    void telegramWebhookRejectsMissingSecret() throws Exception {
        mockMvc.perform(post("/api/v1/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"update_id":1}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el webhook de Telegram rechaza un secret desconocido")
    void telegramWebhookRejectsWrongSecret() throws Exception {
        mockMvc.perform(post("/api/v1/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"update_id":1}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el webhook de Telegram acepta el secret correcto")
    void telegramWebhookAcceptsValidSecret() throws Exception {
        mockMvc.perform(post("/api/v1/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"update_id":1}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un canal inexistente en el JSON devuelve 400, no 500")
    void unknownChannelReturnsBadRequest() throws Exception {
        String apiKey = apiClientService.create("contract-test-channel", null, companyId).plainKey();

        mockMvc.perform(post("/api/v1/notifications")
                        .header(ApiKeyAuthFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"SMS","to":"x","message":"hola"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un request inválido devuelve 400 con el detalle del campo")
    void invalidRequestReturnsValidationDetails() throws Exception {
        String apiKey = apiClientService.create("contract-test-validation", null, companyId).plainKey();

        mockMvc.perform(post("/api/v1/notifications")
                        .header(ApiKeyAuthFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","to":"","message":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").isNotEmpty())
                .andExpect(jsonPath("$.details.message").isNotEmpty());
    }

    @Test
    @DisplayName("el health de actuator sigue siendo público")
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
