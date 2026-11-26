package com.jmcode.notification.email.controller;

import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import com.jmcode.notification.email.dto.EmailAccountRequestDto;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import com.jmcode.notification.email.dto.EmailAccountResponseDto;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import io.swagger.v3.oas.annotations.Operation;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import jakarta.validation.Valid;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import lombok.RequiredArgsConstructor;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.http.HttpStatus;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.PathVariable;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.PostMapping;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.PutMapping;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.RequestBody;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.RequestMapping;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import org.springframework.web.bind.annotation.RestController;

import com.jmcode.notification.email.entity.EmailAccount;
import com.jmcode.notification.email.service.EmailAccountService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/email-accounts")
@Tag(name = "Email Accounts",
        description = "Cuentas SMTP por cliente. El ADMIN de una empresa sólo ve y edita las suyas")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class EmailAccountAdminController {

    private final EmailAccountService accountService;

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
        return EmailAccountResponseDto.from(accountService.save(dto.applyTo(new EmailAccount()), dto.companyId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cuenta de correo SMTP")
    public EmailAccountResponseDto update(@PathVariable Long id, @Valid @RequestBody EmailAccountRequestDto dto) {
        return EmailAccountResponseDto.from(accountService.save(dto.applyTo(accountService.getById(id)), dto.companyId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar cuenta de correo SMTP")
    public void delete(@PathVariable Long id) {
        accountService.delete(id);
    }
}
