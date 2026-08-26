package com.jmcode.notification.telegram;

import com.jmcode.notification.telegram.dto.RegisterWebhookRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
