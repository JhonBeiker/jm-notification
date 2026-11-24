package com.jmcode.notification.whatsapp;

import com.jmcode.notification.whatsapp.dto.WhatsAppDeviceRequestDto;
import com.jmcode.notification.whatsapp.dto.WhatsAppDeviceResponseDto;
import com.jmcode.notification.whatsapp.dto.WhatsAppDeviceStatusResponseDto;
import com.jmcode.notification.whatsapp.dto.WhatsAppPairCodeResponseDto;
import com.jmcode.notification.whatsapp.dto.WhatsAppPairRequestDto;
import com.jmcode.notification.whatsapp.dto.WhatsAppQrResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dispositivos de WhatsApp sobre una instancia
 * <a href="https://github.com/aldinokemal/go-whatsapp-web-multidevice">GOWA</a>. El alta crea
 * la fila local y el dispositivo en GOWA; el emparejamiento se completa escaneando el QR
 * ({@code /qr}) o tecleando el pair code ({@code /pair}), hasta que {@code /status} devuelve
 * {@code loggedIn=true}.
 */
@RestController
@RequestMapping("/api/v1/admin/whatsapp-devices")
@Tag(name = "WhatsApp Devices",
        description = "Dispositivos de WhatsApp (GOWA) por cliente. El ADMIN de una empresa sólo ve y edita los suyos")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class WhatsAppDeviceAdminController {

    private final WhatsAppDeviceService deviceService;

    @GetMapping
    @Operation(summary = "Listar dispositivos de WhatsApp")
    public List<WhatsAppDeviceResponseDto> list() {
        return deviceService.listAll().stream().map(WhatsAppDeviceResponseDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener dispositivo por ID")
    public WhatsAppDeviceResponseDto getById(@PathVariable Long id) {
        return WhatsAppDeviceResponseDto.from(deviceService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear dispositivo de WhatsApp para un cliente",
            description = "Crea la fila local y da de alta el dispositivo en GOWA, que devuelve su deviceId. "
                    + "Con deviceId en el cuerpo adopta uno que ya existe en GOWA (si ya está emparejado, "
                    + "no hace falta QR). Después pide el QR con GET /{id}/qr o el código con POST /{id}/pair.")
    public WhatsAppDeviceResponseDto create(@Valid @RequestBody WhatsAppDeviceRequestDto dto) {
        return WhatsAppDeviceResponseDto.from(
                deviceService.save(dto.applyTo(new WhatsAppDevice()), dto.companyId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar dispositivo de WhatsApp")
    public WhatsAppDeviceResponseDto update(@PathVariable Long id,
                                            @Valid @RequestBody WhatsAppDeviceRequestDto dto) {
        return WhatsAppDeviceResponseDto.from(
                deviceService.save(dto.applyTo(deviceService.getById(id)), dto.companyId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar la fila local",
            description = "No borra el dispositivo en GOWA: para eso usa POST /{id}/unregister antes.")
    public void delete(@PathVariable Long id) {
        deviceService.delete(id);
    }

    @GetMapping("/{id}/qr")
    @Operation(summary = "Obtener el QR para emparejar",
            description = "Devuelve qrLink, una URL a la imagen PNG que sirve la propia instancia GOWA "
                    + "(sin Basic Auth). Caduca en qrDuration segundos: pídelo justo antes de pintarlo.")
    public WhatsAppQrResponseDto qr(@PathVariable Long id) {
        return WhatsAppQrResponseDto.from(deviceService.login(id));
    }

    @PostMapping("/{id}/pair")
    @Operation(summary = "Emparejar por código en vez de por QR",
            description = "Devuelve un código para teclear en el móvil: WhatsApp > Dispositivos vinculados "
                    + "> Vincular con número de teléfono.")
    public WhatsAppPairCodeResponseDto pair(@PathVariable Long id,
                                            @Valid @RequestBody WhatsAppPairRequestDto dto) {
        return WhatsAppPairCodeResponseDto.from(deviceService.pair(id, dto.phoneNumber()));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Estado del dispositivo en GOWA",
            description = "loggedIn=true es la condición para poder enviar.")
    public WhatsAppDeviceStatusResponseDto status(@PathVariable Long id) {
        return WhatsAppDeviceStatusResponseDto.from(deviceService.status(id));
    }

    @PostMapping("/{id}/reconnect")
    @Operation(summary = "Reabrir la sesión de un dispositivo ya emparejado")
    public WhatsAppDeviceStatusResponseDto reconnect(@PathVariable Long id) {
        return WhatsAppDeviceStatusResponseDto.from(deviceService.reconnect(id));
    }

    @PostMapping("/{id}/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar la sesión de WhatsApp",
            description = "Deshace el emparejamiento; el dispositivo sigue existiendo en GOWA.")
    public void logout(@PathVariable Long id) {
        deviceService.logout(id);
    }

    @PostMapping("/{id}/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar el dispositivo en GOWA",
            description = "La fila local se conserva, pero se queda sin deviceId.")
    public void unregister(@PathVariable Long id) {
        deviceService.unregister(id);
    }
}
