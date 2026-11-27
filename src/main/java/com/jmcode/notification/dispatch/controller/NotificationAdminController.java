package com.jmcode.notification.dispatch.controller;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.common.web.ApiError;
import com.jmcode.notification.common.web.PageResponseDto;
import com.jmcode.notification.dispatch.dto.NotificationLogFilter;
import com.jmcode.notification.dispatch.dto.NotificationLogResponseDto;
import com.jmcode.notification.dispatch.dto.NotificationStatsResponseDto;
import com.jmcode.notification.dispatch.service.NotificationLogService;
import com.jmcode.notification.dispatch.service.NotificationQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@Tag(name = "Notifications Admin",
        description = "Historial paginado, detalle y estadísticas de notificaciones enviadas. "
                + "El SUPER_ADMIN puede filtrar por empresa; el ADMIN sólo ve las de su empresa.")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class NotificationAdminController {

    private final NotificationLogService notificationLogService;
    private final NotificationQueueService notificationQueueService;

    @GetMapping
    @Operation(summary = "Listar notificaciones paginadas",
            description = "Ordenadas de más reciente a más antigua. Permite filtrar por empresa (sólo SUPER_ADMIN), "
                    + "canal, estado, destinatario (búsqueda parcial), clientCode y rango de fechas ISO-8601.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de notificaciones"),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación o filtro inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponseDto<NotificationLogResponseDto> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) ChannelType channel,
            @RequestParam(required = false) NotificationStatus status,
            @Parameter(description = "Búsqueda parcial (case-insensitive) por destinatario")
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String clientCode,
            @Parameter(description = "Inicio del rango (ISO-8601, inclusive), p. ej. 2026-01-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Fin del rango (ISO-8601, inclusive), p. ej. 2026-12-31T23:59:59Z")
            @RequestParam(required = false) Instant until,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        NotificationLogFilter filter = new NotificationLogFilter(
                companyId, channel, status, recipient, clientCode, from, until);
        return PageResponseDto.from(
                notificationLogService.list(filter, page, size).map(NotificationLogResponseDto::from)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de una notificación por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la notificación"),
            @ApiResponse(responseCode = "403", description = "La notificación pertenece a otra empresa",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Notificación no encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public NotificationLogResponseDto getById(@PathVariable Long id) {
        return NotificationLogResponseDto.from(notificationLogService.getById(id));
    }

    @GetMapping("/stats")
    @Operation(summary = "Estadísticas de notificaciones",
            description = "Totales agregados por estado y por canal. Acepta filtro por empresa (sólo SUPER_ADMIN) y rango de fechas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen agregado"),
            @ApiResponse(responseCode = "400", description = "Rango de fechas inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public NotificationStatsResponseDto stats(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until
    ) {
        return notificationLogService.stats(companyId, from, until);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reintentar manualmente una notificación fallida",
            description = "Reencola una notificación en estado FAILED que conserve su payload para un nuevo intento.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Notificación reencolada"),
            @ApiResponse(responseCode = "403", description = "La notificación pertenece a otra empresa",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Notificación no encontrada",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "No está en estado FAILED o no tiene payload almacenado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public NotificationLogResponseDto retry(@PathVariable Long id) {
        return NotificationLogResponseDto.from(notificationQueueService.retry(id));
    }
}
