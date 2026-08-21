package com.jmcode.notification.web;

import com.jmcode.notification.telegram.TelegramWebhookService;
import com.jmcode.notification.web.dto.RegisterWebhookRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/telegram/webhook-admin")
@Tag(name = "Telegram Webhook", description = "Registrar / consultar / borrar webhook del bot por código")
public class TelegramWebhookAdminController {

    private final TelegramWebhookService webhookService;

    public TelegramWebhookAdminController(TelegramWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Registrar webhook",
            description = "Llama a Telegram setWebhook. Envía publicBaseUrl (https del túnel/dominio) o configura TELEGRAM_WEBHOOK_URL."
    )
    public Map<String, Object> register(@RequestBody(required = false) RegisterWebhookRequestDto request) {
        String publicBaseUrl = request != null ? request.publicBaseUrl() : null;
        return webhookService.register(publicBaseUrl);
    }

    @GetMapping
    @Operation(summary = "Estado del webhook", description = "Equivale a getWebhookInfo de Telegram.")
    public Map<String, Object> info() {
        return webhookService.info();
    }

    @GetMapping("/bot")
    @Operation(summary = "Info del bot", description = "Equivale a getMe de Telegram.")
    public Map<String, Object> bot() {
        return webhookService.botInfo();
    }

    @DeleteMapping
    @Operation(summary = "Eliminar webhook", description = "Equivale a deleteWebhook de Telegram.")
    public Map<String, Object> unregister() {
        return webhookService.unregister();
    }
}
