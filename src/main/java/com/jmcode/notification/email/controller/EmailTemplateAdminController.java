package com.jmcode.notification.email.controller;

import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import com.jmcode.notification.email.dto.EmailTemplateRequestDto;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import com.jmcode.notification.email.dto.EmailTemplateResponseDto;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import jakarta.validation.Valid;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.http.HttpStatus;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.PathVariable;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.PostMapping;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.PutMapping;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.RequestBody;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.RequestMapping;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
import org.springframework.web.bind.annotation.RestController;

import com.jmcode.notification.email.entity.EmailTemplate;
import com.jmcode.notification.email.service.EmailTemplateService;
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
