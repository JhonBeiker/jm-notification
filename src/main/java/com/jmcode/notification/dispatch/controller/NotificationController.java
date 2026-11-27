package com.jmcode.notification.dispatch.controller;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.common.exception.ChannelNotFoundException;
import com.jmcode.notification.common.web.ApiError;
import com.jmcode.notification.dispatch.dto.BulkNotificationRequestDto;
import com.jmcode.notification.dispatch.dto.NotificationLogResponseDto;
import com.jmcode.notification.dispatch.dto.NotificationRequestDto;
import com.jmcode.notification.dispatch.dto.NotificationResponseDto;
import com.jmcode.notification.dispatch.entity.NotificationLog;
import com.jmcode.notification.dispatch.service.NotificationLogService;
import com.jmcode.notification.dispatch.service.NotificationQueueService;
import com.jmcode.notification.dispatch.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Envío de notificaciones multi-canal (síncrono o encolado con reintentos)")
@SecurityRequirement(name = "apiKey")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationQueueService queueService;
    private final NotificationLogService notificationLogService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enviar notificación",
            description = "Envía una notificación por EMAIL, WHATSAPP o TELEGRAM. "
                    + "Con ?async=true se encola en BD con reintentos automáticos y devuelve el id para consultar estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Notificación aceptada/enviada o encolada"),
            @ApiResponse(responseCode = "400", description = "Request inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Fallo del proveedor (sólo en modo síncrono)",
                    content = @Content(schema = @Schema(implementation = NotificationResponseDto.class)))
    })
    public NotificationResponseDto send(
            @Parameter(description = "Si es true, encola el envío para procesamiento asíncrono con reintentos")
            @RequestParam(defaultValue = "false") boolean async,
            @Valid @RequestBody NotificationRequestDto request
    ) {
        if (async) {
            NotificationLog queued = queueService.enqueue(request.toDomain());
            return NotificationResponseDto.from(
                    NotificationResult.queued(queued.getChannel(), queued.getRecipient()),
                    queued.getId()
            );
        }
        return NotificationResponseDto.from(notificationService.send(request));
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envío masivo",
            description = "Envía varias notificaciones en una sola petición. Con ?async=true encola todo el lote.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Procesamiento completado o lote encolado"),
            @ApiResponse(responseCode = "400", description = "Request inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<NotificationResponseDto> sendBulk(
            @Parameter(description = "Si es true, encola cada notificación del lote para envío asíncrono")
            @RequestParam(defaultValue = "false") boolean async,
            @Valid @RequestBody BulkNotificationRequestDto request
    ) {
        if (async) {
            return request.notifications().stream().map(this::enqueueSafe).toList();
        }
        return notificationService.sendBulk(request).stream().map(NotificationResponseDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar estado de una notificación",
            description = "Permite a la API Key consultar el estado y reintentos de una notificación de su empresa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actual de la notificación"),
            @ApiResponse(responseCode = "403", description = "La notificación pertenece a otra empresa",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Notificación no encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public NotificationLogResponseDto getStatus(@PathVariable Long id) {
        return NotificationLogResponseDto.from(notificationLogService.getById(id));
    }

    @GetMapping("/channels")
    @Operation(summary = "Estado de canales", description = "Indica qué canales están habilitados y configurados.")
    @ApiResponse(responseCode = "200", description = "Mapa canal -> habilitado")
    public Map<ChannelType, Boolean> channels() {
        return notificationService.channelStatus();
    }

    private NotificationResponseDto enqueueSafe(NotificationRequestDto dto) {
        try {
            NotificationLog queued = queueService.enqueue(dto.toDomain());
            return NotificationResponseDto.from(
                    NotificationResult.queued(queued.getChannel(), queued.getRecipient()),
                    queued.getId()
            );
        } catch (ChannelNotFoundException ex) {
            return NotificationResponseDto.from(NotificationResult.failed(dto.channel(), dto.to(), ex.getMessage()));
        }
    }
}
