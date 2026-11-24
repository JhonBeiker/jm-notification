package com.jmcode.notification.whatsapp;

import com.jmcode.notification.common.UpstreamServiceException;
import com.jmcode.notification.company.CompanyScope;
import com.jmcode.notification.email.PasswordEncryptor;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * CRUD de dispositivos de WhatsApp y operaciones de emparejamiento contra GOWA.
 *
 * <p>La resolución de envío es la misma que en Telegram: {@code clientCode} explícito, o el
 * dispositivo por defecto de la empresa del llamante, o el global. Un {@code clientCode} de
 * otra empresa se corta con 403 en {@link CompanyScope#assertCanAccess}.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppDeviceService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppDeviceService.class);

    private final WhatsAppDeviceRepository repository;
    private final WhatsAppDeviceManager deviceManager;
    private final PasswordEncryptor passwordEncryptor;
    private final CompanyScope companyScope;

    @Transactional(readOnly = true)
    public WhatsAppDevice resolveDevice(String clientCode) {
        if (StringUtils.hasText(clientCode)) {
            WhatsAppDevice device = repository.findByClientCodeIgnoreCaseAndActiveTrue(clientCode.trim())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Active WhatsApp device not found for client code: " + clientCode));
            companyScope.assertCanAccess(device.getCompany());
            return device;
        }
        Long companyId = companyScope.filterCompanyId();
        if (companyId != null) {
            return repository.findByCompanyIdAndIsDefaultTrueAndActiveTrue(companyId)
                    .or(() -> repository.findFirstByCompanyIdAndActiveTrueOrderByIdAsc(companyId))
                    .orElseThrow(() -> new EntityNotFoundException(
                            "No active WhatsApp device configured for the company of this caller"));
        }
        return repository.findByIsDefaultTrueAndActiveTrue()
                .or(repository::findFirstByActiveTrueOrderByIdAsc)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active default WhatsApp device configured in database"));
    }

    /** Administración: el ADMIN de una empresa sólo ve los dispositivos de la suya. */
    @Transactional(readOnly = true)
    public List<WhatsAppDevice> listAll() {
        Long companyId = companyScope.filterCompanyId();
        return companyId == null ? repository.findAll() : repository.findAllByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public WhatsAppDevice getById(Long id) {
        WhatsAppDevice device = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("WhatsApp device not found with id=" + id));
        companyScope.assertCanAccess(device.getCompany());
        return device;
    }

    @Transactional
    public WhatsAppDevice save(WhatsAppDevice device, Long requestedCompanyId) {
        boolean isNew = device.getId() == null;
        if (isNew && repository.existsByClientCodeIgnoreCase(device.getClientCode())) {
            throw new IllegalArgumentException(
                    "WhatsApp device already exists for clientCode: " + device.getClientCode());
        }
        device.setCompany(companyScope.resolveOwner(requestedCompanyId));
        if (device.isDefault() && !companyScope.unrestricted()) {
            // El dispositivo por defecto es global: lo usa el envío sin clientCode.
            throw new AccessDeniedException("Only a SUPER_ADMIN can set the global default WhatsApp device");
        }
        if (device.isDefault()) {
            clearPreviousDefault(device);
        }
        String requestedDeviceId = device.getDeviceId();
        if (StringUtils.hasText(requestedDeviceId)) {
            assertDeviceIdIsFree(requestedDeviceId, device.getId());
        }
        device.setBasicAuthPassword(passwordEncryptor.encrypt(device.getBasicAuthPassword()));
        WhatsAppDevice saved = repository.save(device);
        deviceManager.evict(saved.getClientCode());
        if (StringUtils.hasText(requestedDeviceId)) {
            // Adopción: el dispositivo ya existe en GOWA (puede estar incluso emparejado).
            adopt(saved);
        } else if (isNew) {
            // Provisionar aquí evita el estado intermedio en el que la fila existe pero en GOWA
            // no hay dispositivo: si la instancia no responde, el alta se deshace con un 502.
            provision(saved);
        }
        return saved;
    }

    /**
     * Enlaza la fila con un dispositivo que ya existe en GOWA en vez de crear uno nuevo, y se
     * queda con su estado (un dispositivo ya emparejado entra directamente como {@code logged_in},
     * sin pasar por el QR).
     */
    private void adopt(WhatsAppDevice device) {
        Map<String, Object> info;
        try {
            info = deviceManager.getClient(device).getDevice(device.getDeviceId());
        } catch (UpstreamServiceException ex) {
            // GOWA contesta 500 "device X not found" a un id desconocido: eso es un 404 para
            // quien llama, no una avería de la instancia.
            if (isNotFound(ex)) {
                throw new EntityNotFoundException(
                        "GOWA has no device with id: " + device.getDeviceId()
                                + ". List the available ones in the GOWA instance, or omit deviceId to create a new one");
            }
            throw ex;
        }
        applyStatus(device, info);
        log.info("GOWA device {} adopted (clientCode={} state={})",
                device.getDeviceId(), device.getClientCode(), device.getStatus());
    }

    private void assertDeviceIdIsFree(String deviceId, Long ownId) {
        repository.findByDeviceIdIgnoreCase(deviceId)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("GOWA device " + deviceId
                            + " is already linked to clientCode: " + existing.getClientCode());
                });
    }

    private static boolean isNotFound(UpstreamServiceException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("not found");
    }

    /**
     * Da de alta un dispositivo nuevo en GOWA y guarda el id que devuelve. Es el camino por
     * defecto: sólo se salta cuando el request trae un {@code deviceId} que adoptar.
     */
    private void provision(WhatsAppDevice device) {
        Map<String, Object> created = deviceManager.getClient(device).createDevice();
        Object deviceId = created.get("id");
        if (deviceId == null) {
            throw new IllegalStateException("GOWA did not return a device id when creating the device");
        }
        device.setDeviceId(String.valueOf(deviceId));
        device.setStatus(textOrNull(created.get("state")));
        repository.save(device);
        log.info("GOWA device {} provisioned (clientCode={})", device.getDeviceId(), device.getClientCode());
    }

    /** Borra sólo la fila local. El dispositivo sigue existiendo en GOWA hasta hacer unregister. */
    @Transactional
    public void delete(Long id) {
        WhatsAppDevice device = getById(id);
        repository.delete(device);
        deviceManager.evict(device.getClientCode());
    }

    /**
     * Pide el QR para emparejar. Devuelve {@code qr_link} (un PNG que sirve la propia instancia)
     * y {@code qr_duration}.
     */
    @Transactional
    public Map<String, Object> login(Long id) {
        WhatsAppDevice device = requireProvisioned(id);
        Map<String, Object> response = deviceManager.getClient(device).login(device.getDeviceId());
        device.setStatus("waiting_for_qr");
        repository.save(device);
        return response;
    }

    /** Emparejamiento por código: devuelve el código a teclear en el móvil. */
    @Transactional
    public Map<String, Object> pair(Long id, String phoneNumber) {
        WhatsAppDevice device = requireProvisioned(id);
        Map<String, Object> response = deviceManager.getClient(device)
                .loginWithCode(device.getDeviceId(), normalizePhone(phoneNumber));
        device.setStatus("waiting_for_pair_code");
        repository.save(device);
        log.info("GOWA pair code requested for device {} (clientCode={})",
                device.getDeviceId(), device.getClientCode());
        return response;
    }

    /**
     * Reabre la sesión de un dispositivo ya emparejado, sin volver a pedir QR. Devuelve el
     * estado resultante, que es lo que decide si aún hace falta emparejar.
     */
    @Transactional
    public Map<String, Object> reconnect(Long id) {
        WhatsAppDevice device = requireProvisioned(id);
        GowaClient client = deviceManager.getClient(device);
        client.reconnect(device.getDeviceId());
        return applyStatus(device, client.getDevice(device.getDeviceId()));
    }

    /** Estado en GOWA. De paso refresca el número y el estado guardados en la fila. */
    @Transactional
    public Map<String, Object> status(Long id) {
        WhatsAppDevice device = requireProvisioned(id);
        return applyStatus(device, deviceManager.getClient(device).getDevice(device.getDeviceId()));
    }

    /** Cierra la sesión de WhatsApp; el dispositivo sigue existiendo en GOWA. */
    @Transactional
    public void logout(Long id) {
        WhatsAppDevice device = requireProvisioned(id);
        deviceManager.getClient(device).logout(device.getDeviceId());
        device.setStatus("logged_out");
        device.setPhoneNumber(null);
        repository.save(device);
        log.info("GOWA device {} logged out (clientCode={})", device.getDeviceId(), device.getClientCode());
    }

    /** Borra el dispositivo en GOWA y deja la fila local sin {@code deviceId}. */
    @Transactional
    public void unregister(Long id) {
        WhatsAppDevice device = requireProvisioned(id);
        deviceManager.getClient(device).deleteDevice(device.getDeviceId());
        device.setDeviceId(null);
        device.setStatus("removed");
        device.setPhoneNumber(null);
        repository.save(device);
        log.info("GOWA device removed remotely (clientCode={})", device.getClientCode());
    }

    private WhatsAppDevice requireProvisioned(Long id) {
        WhatsAppDevice device = getById(id);
        if (!StringUtils.hasText(device.getDeviceId())) {
            throw new IllegalStateException(
                    "This WhatsApp row has no GOWA device yet. Re-create it, or check that the GOWA "
                            + "instance was reachable when it was saved");
        }
        return device;
    }

    /**
     * Vuelca sobre la fila lo que reporta GOWA. El JID trae el número por delante de la arroba
     * ({@code 584263073306@s.whatsapp.net}), que es el dato útil para el administrador.
     */
    private Map<String, Object> applyStatus(WhatsAppDevice device, Map<String, Object> info) {
        String state = textOrNull(info.get("state"));
        if (state != null) {
            device.setStatus(state);
        }
        String jid = textOrNull(info.get("jid"));
        if (StringUtils.hasText(jid)) {
            int at = jid.indexOf('@');
            device.setPhoneNumber(at > 0 ? jid.substring(0, at) : jid);
        }
        repository.save(device);
        return info;
    }

    private void clearPreviousDefault(WhatsAppDevice device) {
        repository.findByIsDefaultTrueAndActiveTrue()
                .filter(existing -> !existing.getId().equals(device.getId()))
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    repository.save(existing);
                });
    }

    /** GOWA espera el número sin {@code +} ni separadores. */
    private static String normalizePhone(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
    }

    private static String textOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
