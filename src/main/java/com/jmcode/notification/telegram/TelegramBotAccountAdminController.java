package com.jmcode.notification.telegram;

import com.jmcode.notification.telegram.dto.TelegramBotAccountRequestDto;
import com.jmcode.notification.telegram.dto.TelegramBotAccountResponseDto;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/telegram-bot-accounts")
@Tag(name = "Telegram Bot Accounts", description = "Gestión de cuentas de bots de Telegram por cliente almacenadas en Base de Datos")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class TelegramBotAccountAdminController {

    private final TelegramBotAccountService accountService;

    @GetMapping
    @Operation(summary = "Listar cuentas de bots de Telegram")
    public List<TelegramBotAccountResponseDto> list() {
        return accountService.listAll().stream().map(TelegramBotAccountResponseDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cuenta por ID")
    public TelegramBotAccountResponseDto getById(@PathVariable Long id) {
        return TelegramBotAccountResponseDto.from(accountService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear cuenta de bot de Telegram para un cliente")
    public TelegramBotAccountResponseDto create(@Valid @RequestBody TelegramBotAccountRequestDto dto) {
        return TelegramBotAccountResponseDto.from(accountService.save(dto.applyTo(new TelegramBotAccount())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cuenta de bot de Telegram")
    public TelegramBotAccountResponseDto update(@PathVariable Long id, @Valid @RequestBody TelegramBotAccountRequestDto dto) {
        return TelegramBotAccountResponseDto.from(accountService.save(dto.applyTo(accountService.getById(id))));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar cuenta de bot de Telegram")
    public void delete(@PathVariable Long id) {
        accountService.delete(id);
    }
}
