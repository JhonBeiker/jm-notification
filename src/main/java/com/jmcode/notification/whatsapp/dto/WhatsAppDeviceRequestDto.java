package com.jmcode.notification.whatsapp.dto;

import com.jmcode.notification.whatsapp.WhatsAppDevice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

/**
 * Alta y edición de un dispositivo. {@code deviceId} sólo se manda para <em>adoptar</em> uno que
 * ya existe en GOWA; omitirlo es lo normal, y entonces GOWA crea el dispositivo y elige el id.
 */
public record WhatsAppDeviceRequestDto(

        @NotBlank @Size(max = 64) String clientCode,

        /** Empresa propietaria. Obligatorio para SUPER_ADMIN; el ADMIN de empresa usa siempre la suya. */
        Long companyId,

        /**
         * Id de un dispositivo que ya existe en GOWA, para adoptarlo en vez de crear uno nuevo
         * (p. ej. uno ya emparejado). Vacío = GOWA crea el dispositivo y asigna el id.
         */
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9._-]*", message = "deviceId only allows A-Z a-z 0-9 . _ -")
        String deviceId,

        @Size(max = 128) String name,

        /** Vacío = instancia GOWA global ({@code notification.whatsapp.api-url}). */
        @Size(max = 255) String apiUrl,

        /** Vacío = credenciales globales. Al informarlo, la password acompaña a este usuario. */
        @Size(max = 128) String basicAuthUser,

        /** Vacío = la global; en una actualización, vacío conserva la ya guardada. */
        @Size(max = 512) String basicAuthPassword,

        boolean active,
        boolean isDefault
) {

    public WhatsAppDevice applyTo(WhatsAppDevice entity) {
        entity.setClientCode(clientCode.trim());
        // Vacío no borra el id ya guardado: en una edición se conserva el dispositivo actual.
        if (StringUtils.hasText(deviceId)) {
            entity.setDeviceId(deviceId.trim());
        }
        entity.setName(name);
        entity.setApiUrl(StringUtils.hasText(apiUrl) ? apiUrl.trim() : null);
        entity.setBasicAuthUser(StringUtils.hasText(basicAuthUser) ? basicAuthUser.trim() : null);
        if (StringUtils.hasText(basicAuthPassword)) {
            entity.setBasicAuthPassword(basicAuthPassword);
        }
        entity.setActive(active);
        entity.setDefault(isDefault);
        return entity;
    }
}
