package com.jmcode.notification.dispatch.controller;

import com.jmcode.notification.common.web.ApiError;
import com.jmcode.notification.common.web.PageResponseDto;
import com.jmcode.notification.dispatch.dto.NotificationLogResponseDto;
import com.jmcode.notification.dispatch.service.NotificationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@Tag(name = "Notifications Admin",
        description = "Historial paginado de notificaciones enviadas. El SUPER_ADMIN puede filtrar por empresa; "
                + "el ADMIN sólo ve las de su empresa.")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class NotificationAdminController {

    private final NotificationLogService notificationLogService;

    @GetMapping
    @Operation(summary = "Listar notificaciones paginadas",
            description = "Ordenadas de más reciente a más antigua. Filtra por empresa con ?companyId= (sólo SUPER_ADMIN).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de notificaciones"),
            @ApiResponse(responseCode = "400", description = "Parámetros de paginación inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponseDto<NotificationLogResponseDto> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponseDto.from(
                notificationLogService.list(companyId, page, size).map(NotificationLogResponseDto::from)
        );
    }
}
