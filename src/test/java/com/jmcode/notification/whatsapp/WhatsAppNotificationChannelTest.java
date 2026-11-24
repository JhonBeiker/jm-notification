package com.jmcode.notification.whatsapp;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.common.UpstreamServiceException;
import com.jmcode.notification.config.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificationChannelTest {

    @Mock
    private WhatsAppDeviceService deviceService;

    @Mock
    private WhatsAppDeviceManager deviceManager;

    @Mock
    private GowaClient gowaClient;

    private WhatsAppDevice device;

    @BeforeEach
    void setUp() {
        device = new WhatsAppDevice();
        device.setClientCode("acme");
        device.setDeviceId("c9bf3acf-1096-4b46-aeae-06e578fa05d4");
    }

    private WhatsAppNotificationChannel channel(boolean enabled) {
        NotificationProperties properties = new NotificationProperties(
                new NotificationProperties.Email(true, "", ""),
                new NotificationProperties.WhatsApp(enabled, "http://gowa.local", "admin", "secret"),
                new NotificationProperties.Telegram(false, "", "", true, "", "", "", false,
                        Duration.ofMinutes(2), Duration.ofSeconds(10)));
        return new WhatsAppNotificationChannel(deviceService, deviceManager, properties);
    }

    private static NotificationRequest requestTo(String to) {
        return new NotificationRequest(ChannelType.WHATSAPP, to, null, "hola", "acme",
                Map.of(), null, Map.of(), List.of());
    }

    private void givenConfiguredDevice() {
        when(deviceService.resolveDevice("acme")).thenReturn(device);
        when(deviceManager.getClient(device)).thenReturn(gowaClient);
        when(gowaClient.isConfigured()).thenReturn(true);
    }

    @Test
    void skipsWhenChannelDisabled() {
        NotificationResult result = channel(false).send(requestTo("+34600111222"));

        assertEquals(NotificationStatus.SKIPPED, result.status());
        verifyNoInteractions(deviceService, deviceManager);
    }

    /** GOWA quiere el número sin '+' ni separadores. */
    @Test
    void sendsNormalizedPhoneNumberThroughTheResolvedDevice() {
        givenConfiguredDevice();
        when(gowaClient.sendText(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("message_id", "3EB0ABC"));

        NotificationResult result = channel(true).send(requestTo("+58 426 307 3306"));

        assertEquals(NotificationStatus.SENT, result.status());
        assertEquals("3EB0ABC", result.providerMessageId());
        verify(gowaClient).sendText(device.getDeviceId(), "584263073306", "hola");
    }

    /** Un JID completo (grupo o usuario) se respeta tal cual, sin quitarle el sufijo. */
    @Test
    void keepsJidRecipientsUntouched() {
        givenConfiguredDevice();
        when(gowaClient.sendText(anyString(), anyString(), anyString())).thenReturn(Map.of());

        NotificationResult result = channel(true).send(requestTo("120363000000000000@g.us"));

        assertEquals(NotificationStatus.SENT, result.status());
        verify(gowaClient).sendText(device.getDeviceId(), "120363000000000000@g.us", "hola");
    }

    @Test
    void failsOnRecipientThatIsNotAPhoneNorAJid() {
        when(deviceService.resolveDevice("acme")).thenReturn(device);

        NotificationResult result = channel(true).send(requestTo("john-doe"));

        assertEquals(NotificationStatus.FAILED, result.status());
        assertTrue(result.detail().contains("Invalid WhatsApp recipient"));
        verifyNoInteractions(deviceManager);
    }

    /** Fila creada mientras GOWA no respondía: no hay dispositivo contra el que enviar. */
    @Test
    void failsWhenTheRowHasNoGowaDeviceYet() {
        device.setDeviceId(null);
        when(deviceService.resolveDevice("acme")).thenReturn(device);

        NotificationResult result = channel(true).send(requestTo("+584263073306"));

        assertEquals(NotificationStatus.FAILED, result.status());
        assertTrue(result.detail().contains("not provisioned"));
        verifyNoInteractions(deviceManager);
    }

    /** Dispositivo sin emparejar: GOWA responde 401 y el detalle tiene que llegar al cliente. */
    @Test
    void reportsNotLoggedInAsAFailedResult() {
        givenConfiguredDevice();
        when(gowaClient.sendText(anyString(), anyString(), anyString()))
                .thenThrow(new UpstreamServiceException("gowa", 401,
                        "GOWA responded 401: {\"code\":\"AUTHENTICATION_ERROR\",\"message\":\"you are not logged in\"}",
                        null));

        NotificationResult result = channel(true).send(requestTo("+584263073306"));

        assertEquals(NotificationStatus.FAILED, result.status());
        assertTrue(result.detail().contains("you are not logged in"));
    }

    /** Un clientCode de otra empresa debe salir como 403, no como fallo del proveedor (502). */
    @Test
    void propagatesAccessDenied() {
        when(deviceService.resolveDevice("acme"))
                .thenThrow(new AccessDeniedException("Record belongs to another company"));

        WhatsAppNotificationChannel channel = channel(true);
        NotificationRequest request = requestTo("+584263073306");

        assertThrows(AccessDeniedException.class, () -> channel.send(request));
    }
}
