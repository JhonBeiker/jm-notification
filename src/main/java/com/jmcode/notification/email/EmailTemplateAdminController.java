package com.jmcode.notification.email;

import com.jmcode.notification.email.dto.EmailTemplateRequestDto;
import com.jmcode.notification.email.dto.EmailTemplateResponseDto;
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
 * Plantillas de correo por empresa. Antes sólo existían en base de datos, sin ningún
 * endpoint para cargarlas: el administrador de la empresa las gestiona desde aquí.
 */
@RestController
@RequestMapping("/api/v1/admin/email-templates")
@Tag(name = "Email Templates",
        description = "Plantillas de correo por empresa. El ADMIN de una empresa sólo ve y edita las suyas")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class EmailTemplateAdminController {

    private final EmailTemplateService templateService;

    @GetMapping
    @Operation(summary = "Listar plantillas")
    public List<EmailTemplateResponseDto> list() {
        return templateService.listAll().stream().map(EmailTemplateResponseDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener plantilla por ID")
    public EmailTemplateResponseDto getById(@PathVariable Long id) {
        return EmailTemplateResponseDto.from(templateService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear plantilla", description = "Los placeholders tienen la forma {{variable}}.")
    public EmailTemplateResponseDto create(@Valid @RequestBody EmailTemplateRequestDto dto) {
        return EmailTemplateResponseDto.from(
                templateService.save(dto.applyTo(new EmailTemplate()), dto.companyId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar plantilla")
    public EmailTemplateResponseDto update(@PathVariable Long id, @Valid @RequestBody EmailTemplateRequestDto dto) {
        return EmailTemplateResponseDto.from(
                templateService.save(dto.applyTo(templateService.getById(id)), dto.companyId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar plantilla")
    public void delete(@PathVariable Long id) {
        templateService.delete(id);
    }
}
