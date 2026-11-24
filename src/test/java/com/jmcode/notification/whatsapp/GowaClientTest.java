package com.jmcode.notification.whatsapp;

import com.jmcode.notification.common.UpstreamServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GowaClientTest {

    private static final String BASE = "http://gowa.local";
    private static final String DEVICE = "c9bf3acf-1096-4b46-aeae-06e578fa05d4";
    private static final String EXPECTED_BASIC =
            "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    private MockRestServiceServer server;
    private GowaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GowaClient(builder.build(), BASE + "/", "admin", "secret");
    }

    /** Las respuestas vienen envueltas en {code, message, results}: el cliente desenvuelve. */
    @Test
    void unwrapsResultsAndSendsBasicAuthPlusDeviceHeader() {
        server.expect(requestTo(BASE + "/send/message"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", EXPECTED_BASIC))
                .andExpect(header("X-Device-Id", DEVICE))
                .andRespond(withSuccess("{\"code\":\"SUCCESS\",\"message\":\"ok\","
                        + "\"results\":{\"message_id\":\"3EB0\"}}", MediaType.APPLICATION_JSON));

        Map<String, Object> results = client.sendText(DEVICE, "584263073306", "hola");

        assertEquals("3EB0", results.get("message_id"));
        server.verify();
    }

    @Test
    void loginReturnsTheQrLinkAndItsDuration() {
        server.expect(requestTo(BASE + "/app/login"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Device-Id", DEVICE))
                .andRespond(withSuccess("{\"code\":\"SUCCESS\",\"results\":{\"device_id\":\"" + DEVICE + "\","
                        + "\"qr_duration\":30,\"qr_link\":\"http://gowa.local/statics/qrcode/scan-qr-1.png\"}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> results = client.login(DEVICE);

        assertEquals("http://gowa.local/statics/qrcode/scan-qr-1.png", results.get("qr_link"));
        assertEquals(30, results.get("qr_duration"));
    }

    @Test
    void listDevicesReadsTheResultsArray() {
        server.expect(requestTo(BASE + "/devices"))
                .andRespond(withSuccess("{\"code\":\"SUCCESS\",\"results\":["
                        + "{\"id\":\"a\",\"state\":\"logged_in\"},{\"id\":\"b\",\"state\":\"disconnected\"}]}",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> devices = client.listDevices();

        assertEquals(2, devices.size());
        assertEquals("logged_in", devices.get(0).get("state"));
    }

    /** Dispositivo sin emparejar: 401 con el motivo, que debe llegar íntegro al detalle. */
    @Test
    void wrapsNotLoggedInAsUpstreamFailureKeepingTheProviderMessage() {
        server.expect(requestTo(BASE + "/send/message"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"AUTHENTICATION_ERROR\",\"message\":\"you are not logged in\"}"));

        UpstreamServiceException ex = assertThrows(UpstreamServiceException.class,
                () -> client.sendText(DEVICE, "584263073306", "hola"));

        assertEquals(401, ex.getStatus());
        assertTrue(ex.getMessage().contains("you are not logged in"));
    }

    /** Una instancia sin APP_BASIC_AUTH es válida: no se manda cabecera de autorización. */
    @Test
    void worksAgainstAnInstanceWithoutBasicAuth() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer plainServer = MockRestServiceServer.bindTo(builder).build();
        GowaClient plain = new GowaClient(builder.build(), BASE, "", "");
        plainServer.expect(requestTo(BASE + "/devices"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertEquals(List.of(), plain.listDevices());
        plainServer.verify();
    }

    @Test
    void failsFastWhenApiUrlIsMissing() {
        GowaClient unconfigured = new GowaClient(RestClient.builder().build(), "", "admin", "secret");

        assertThrows(IllegalStateException.class, () -> unconfigured.getDevice(DEVICE));
    }
}
