package com.jmcode.notification.security;

import com.jmcode.notification.security.dto.ApiClientResponseDto;
import com.jmcode.notification.security.dto.CreateApiClientRequestDto;
import com.jmcode.notification.security.dto.CreatedApiClientResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/api-clients")
@Tag(name = "Api Clients",
        description = "Clientes con API Key. Cada clave pertenece a una empresa y sólo envía por sus cuentas; "
                + "el ADMIN de una empresa gestiona las suyas")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class ApiClientAdminController {

    private final ApiClientService apiClientService;

    @GetMapping
    @Operation(summary = "Listar clientes registrados", description = "Filtra por empresa con ?companyId=")
    public List<ApiClientResponseDto> list(@RequestParam(required = false) Long companyId) {
        return apiClientService.list(companyId).stream().map(ApiClientResponseDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear cliente para una empresa; devuelve la API Key UNA sola vez",
            description = "El SUPER_ADMIN indica companyId; el ADMIN de una empresa lo omite y usa la suya.")
    public CreatedApiClientResponseDto create(@Valid @RequestBody CreateApiClientRequestDto dto) {
        var created = apiClientService.create(dto.name(), dto.contactEmail(), dto.companyId());
        return CreatedApiClientResponseDto.from(created.client(), created.plainKey());
    }

    @PostMapping("/{id}/rotate-key")
    @Operation(summary = "Rotar la API Key de un cliente; devuelve la nueva key UNA sola vez")
    public CreatedApiClientResponseDto rotate(@PathVariable Long id) {
        String plainKey = apiClientService.rotateKey(id);
        var client = apiClientService.getById(id);
        return CreatedApiClientResponseDto.from(client, plainKey);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un cliente")
    public void delete(@PathVariable Long id) {
        apiClientService.delete(id);
    }
}
