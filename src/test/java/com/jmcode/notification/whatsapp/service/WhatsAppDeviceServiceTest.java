package com.jmcode.notification.whatsapp.service;

import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import com.jmcode.notification.common.exception.UpstreamServiceException;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import com.jmcode.notification.company.service.CompanyScope;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import com.jmcode.notification.email.service.PasswordEncryptor;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import jakarta.persistence.EntityNotFoundException;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import org.junit.jupiter.api.Test;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import org.mockito.Mock;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import org.mockito.junit.jupiter.MockitoExtension;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import org.mockito.junit.jupiter.MockitoSettings;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import org.mockito.quality.Strictness;

import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import java.util.Map;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import java.util.Optional;

import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import static org.mockito.ArgumentMatchers.any;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import static org.mockito.ArgumentMatchers.anyString;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import static org.mockito.Mockito.never;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import static org.mockito.Mockito.verify;
import com.jmcode.notification.whatsapp.client.GowaClient;
import com.jmcode.notification.whatsapp.entity.WhatsAppDevice;
import com.jmcode.notification.whatsapp.repository.WhatsAppDeviceRepository;
import static org.mockito.Mockito.when;

/** Alta de un dispositivo: crear uno nuevo en GOWA frente a adoptar uno que ya existe. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppDeviceServiceTest {

    @Mock
    private WhatsAppDeviceRepository repository;

    @Mock
    private WhatsAppDeviceManager deviceManager;

    @Mock
    private PasswordEncryptor passwordEncryptor;

    @Mock
    private CompanyScope companyScope;

    @Mock
    private GowaClient gowaClient;

    private WhatsAppDeviceService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppDeviceService(repository, deviceManager, passwordEncryptor, companyScope);
        when(repository.save(any(WhatsAppDevice.class))).thenAnswer(call -> call.getArgument(0));
        when(deviceManager.getClient(any(WhatsAppDevice.class))).thenReturn(gowaClient);
        when(repository.findByDeviceIdIgnoreCase(anyString())).thenReturn(Optional.empty());
    }

    private static WhatsAppDevice newDevice(String deviceId) {
        WhatsAppDevice device = new WhatsAppDevice();
        device.setClientCode("acme");
        device.setDeviceId(deviceId);
        device.setActive(true);
        return device;
    }

    @Test
    void createsANewGowaDeviceWhenNoDeviceIdIsGiven() {
        when(gowaClient.createDevice())
                .thenReturn(Map.of("id", "909423a5-5b28", "state", "disconnected"));

        WhatsAppDevice saved = service.save(newDevice(null), null);

        assertEquals("909423a5-5b28", saved.getDeviceId());
        assertEquals("disconnected", saved.getStatus());
    }

    /** Adoptar uno ya emparejado entra directamente como logged_in, sin pasar por el QR. */
    @Test
    void adoptsAnExistingDeviceAndTakesItsStateAndPhone() {
        when(gowaClient.getDevice("prueba")).thenReturn(Map.of(
                "id", "prueba",
                "display_name", "Jhon Moran",
                "state", "logged_in",
                "jid", "584263073306@s.whatsapp.net"));

        WhatsAppDevice saved = service.save(newDevice("prueba"), null);

        assertEquals("prueba", saved.getDeviceId());
        assertEquals("logged_in", saved.getStatus());
        assertEquals("584263073306", saved.getPhoneNumber());
        verify(gowaClient, never()).createDevice();
    }

    /** GOWA contesta 500 "device X not found" a un id desconocido: para el cliente es un 404. */
    @Test
    void rejectsAdoptingADeviceThatGowaDoesNotHave() {
        when(gowaClient.getDevice("ghost")).thenThrow(new UpstreamServiceException("gowa", 500,
                "GOWA responded 500: {\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"device ghost not found\"}",
                null));

        WhatsAppDevice device = newDevice("ghost");

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> service.save(device, null));
        assertTrue(ex.getMessage().contains("GOWA has no device with id: ghost"));
    }

    /** Dos clientes apuntando al mismo dispositivo se pisarían los mensajes. */
    @Test
    void rejectsADeviceIdAlreadyLinkedToAnotherRow() {
        WhatsAppDevice other = newDevice("prueba");
        other.setClientCode("otra-empresa");
        other.setId(7L);
        when(repository.findByDeviceIdIgnoreCase("prueba")).thenReturn(Optional.of(other));

        WhatsAppDevice device = newDevice("prueba");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.save(device, null));
        assertTrue(ex.getMessage().contains("already linked to clientCode: otra-empresa"));
    }

    /** Una avería real de GOWA sigue siendo 502: no se disfraza de "no existe". */
    @Test
    void propagatesRealGowaFailuresWhileAdopting() {
        when(gowaClient.getDevice("prueba")).thenThrow(new UpstreamServiceException("gowa", 0,
                "GOWA server is unreachable at http://gowa.local: connection refused", null));

        WhatsAppDevice device = newDevice("prueba");

        assertThrows(UpstreamServiceException.class, () -> service.save(device, null));
    }
}
