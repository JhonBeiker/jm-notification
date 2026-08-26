package com.jmcode.notification.dispatch;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.dispatch.dto.BulkNotificationRequestDto;
import com.jmcode.notification.dispatch.dto.NotificationRequestDto;
import com.jmcode.notification.dispatch.dto.NotificationResponseDto;
import com.jmcode.notification.common.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Envío de notificaciones multi-canal")
@SecurityRequirement(name = "apiKey")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enviar notificación", description = "Envía una notificación por EMAIL, WHATSAPP o TELEGRAM.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Notificación aceptada/enviada"),
            @ApiResponse(responseCode = "400", description = "Request inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Fallo del proveedor",
                    content = @Content(schema = @Schema(implementation = NotificationResponseDto.class)))
    })
    public NotificationResponseDto send(@Valid @RequestBody NotificationRequestDto request) {
        return NotificationResponseDto.from(notificationService.send(request));
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envío masivo", description = "Envía varias notificaciones en una sola petición.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Procesamiento completado"),
            @ApiResponse(responseCode = "400", description = "Request inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<NotificationResponseDto> sendBulk(@Valid @RequestBody BulkNotificationRequestDto request) {
        return notificationService.sendBulk(request).stream().map(NotificationResponseDto::from).toList();
    }

    @GetMapping("/channels")
    @Operation(summary = "Estado de canales", description = "Indica qué canales están habilitados y configurados.")
    @ApiResponse(responseCode = "200", description = "Mapa canal -> habilitado")
    public Map<ChannelType, Boolean> channels() {
        return notificationService.channelStatus();
    }
}
