package com.jmcode.notification.telegram.controller;

import com.jmcode.notification.telegram.service.TelegramWebhookService;
import com.jmcode.notification.telegram.dto.RegisterWebhookRequestDto;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import lombok.RequiredArgsConstructor;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.http.HttpStatus;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.web.bind.annotation.GetMapping;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.web.bind.annotation.PostMapping;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.web.bind.annotation.RequestBody;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.web.bind.annotation.RequestMapping;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.web.bind.annotation.RequestParam;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.jmcode.notification.telegram.service.TelegramWebhookService;
import org.springframework.web.bind.annotation.RestController;

import com.jmcode.notification.telegram.service.TelegramWebhookService;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/telegram/webhook-admin")
@Tag(name = "Telegram Webhook", description = "Registrar / consultar / borrar webhook del bot por cliente")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class TelegramWebhookAdminController {

    private final TelegramWebhookService webhookService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Registrar webhook",
            description = "Llama a Telegram setWebhook. Envía publicBaseUrl (https del túnel/dominio) o configura webhookUrl en la cuenta del bot."
    )
    public Map<String, Object> register(@RequestBody(required = false) RegisterWebhookRequestDto request) {
        String publicBaseUrl = request != null ? request.publicBaseUrl() : null;
        String clientCode = request != null ? request.clientCode() : null;
        return webhookService.register(publicBaseUrl, clientCode);
    }

    @GetMapping
    @Operation(summary = "Estado del webhook", description = "Equivale a getWebhookInfo de Telegram.")
    public Map<String, Object> info(@RequestParam(required = false) String clientCode) {
        return webhookService.info(clientCode);
    }

    @GetMapping("/bot")
    @Operation(summary = "Info del bot", description = "Equivale a getMe de Telegram.")
    public Map<String, Object> bot(@RequestParam(required = false) String clientCode) {
        return webhookService.botInfo(clientCode);
    }

    @DeleteMapping
    @Operation(summary = "Eliminar webhook", description = "Equivale a deleteWebhook de Telegram.")
    public Map<String, Object> unregister(@RequestParam(required = false) String clientCode) {
        return webhookService.unregister(clientCode);
    }
}
