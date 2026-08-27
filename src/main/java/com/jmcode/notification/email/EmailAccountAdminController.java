package com.jmcode.notification.email;

import com.jmcode.notification.email.dto.EmailAccountRequestDto;
import com.jmcode.notification.email.dto.EmailAccountResponseDto;
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
