package com.jmcode.notification.web;

import com.jmcode.notification.email.EmailAccount;
import com.jmcode.notification.email.EmailAccountService;
import com.jmcode.notification.web.dto.EmailAccountRequestDto;
import com.jmcode.notification.web.dto.EmailAccountResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1/email-accounts")
@Tag(name = "Email Accounts", description = "Gestión de cuentas SMTP por cliente almacenadas en Base de Datos")
public class EmailAccountAdminController {

    private final EmailAccountService accountService;

    public EmailAccountAdminController(EmailAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "Listar cuentas de correo")
    public List<EmailAccountResponseDto> list() {
        return accountService.listAll().stream().map(EmailAccountResponseDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cuenta por ID")
    public EmailAccountResponseDto getById(@PathVariable Long id) {
        return EmailAccountResponseDto.from(accountService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear cuenta de correo SMTP para un cliente")
    public EmailAccountResponseDto create(@Valid @RequestBody EmailAccountRequestDto dto) {
        EmailAccount account = toEntity(dto, new EmailAccount());
        return EmailAccountResponseDto.from(accountService.save(account));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cuenta de correo SMTP")
    public EmailAccountResponseDto update(@PathVariable Long id, @Valid @RequestBody EmailAccountRequestDto dto) {
        EmailAccount existing = accountService.getById(id);
        EmailAccount account = toEntity(dto, existing);
        return EmailAccountResponseDto.from(accountService.save(account));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar cuenta de correo SMTP")
    public void delete(@PathVariable Long id) {
        accountService.delete(id);
    }

    private static EmailAccount toEntity(EmailAccountRequestDto dto, EmailAccount entity) {
        entity.setClientCode(dto.clientCode().trim());
        entity.setName(dto.name());
        entity.setHost(dto.host().trim());
        entity.setPort(dto.port());
        entity.setUsername(dto.username().trim());
        entity.setPassword(dto.password());
        entity.setFromAddress(dto.fromAddress());
        entity.setFromName(dto.fromName());
        entity.setReplyTo(dto.replyTo());
        entity.setProtocol(dto.protocol() != null ? dto.protocol() : "smtp");
        entity.setAuth(dto.auth());
        entity.setStarttlsEnable(dto.starttlsEnable());
        entity.setStarttlsRequired(dto.starttlsRequired());
        entity.setSslEnable(dto.sslEnable());
        entity.setActive(dto.active());
        entity.setDefault(dto.isDefault());
        if (dto.connectionTimeoutMs() > 0) entity.setConnectionTimeoutMs(dto.connectionTimeoutMs());
        if (dto.timeoutMs() > 0) entity.setTimeoutMs(dto.timeoutMs());
        if (dto.writeTimeoutMs() > 0) entity.setWriteTimeoutMs(dto.writeTimeoutMs());
        return entity;
    }
}
