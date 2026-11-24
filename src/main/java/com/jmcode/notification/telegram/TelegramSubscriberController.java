package com.jmcode.notification.telegram;

import com.jmcode.notification.telegram.dto.LinkExternalUserRequestDto;
import com.jmcode.notification.telegram.dto.TelegramSubscriberResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/telegram/subscribers")
@Tag(name = "Telegram Subscribers", description = "Usuarios que iniciaron el bot y pueden recibir mensajes")
@SecurityRequirement(name = "adminJwt")
@RequiredArgsConstructor
public class TelegramSubscriberController {

    private final TelegramSubscriberService subscriberService;

    @GetMapping
    @Operation(summary = "Listar suscriptores", description = "Por defecto solo activos. Usa activeOnly=false para todos.")
    public List<TelegramSubscriberResponseDto> list(
            @RequestParam(name = "activeOnly", defaultValue = "true") boolean activeOnly
    ) {
        List<TelegramSubscriber> subscribers = activeOnly ? subscriberService.listActive() : subscriberService.listAll();
        return subscribers.stream().map(TelegramSubscriberResponseDto::from).toList();
    }

    @GetMapping("/{chatId}")
    @Operation(summary = "Obtener suscriptor por chat_id")
    public TelegramSubscriberResponseDto getByChatId(@PathVariable Long chatId) {
        return TelegramSubscriberResponseDto.from(subscriberService.getByChatId(chatId));
    }

    @PutMapping("/link")
    @Operation(summary = "Vincular externalUserId", description = "Asocia un id de tu sistema al chat_id de Telegram.")
    public TelegramSubscriberResponseDto link(@Valid @RequestBody LinkExternalUserRequestDto request) {
        return TelegramSubscriberResponseDto.from(
                subscriberService.linkExternalUser(request.chatId(), request.externalUserId()));
    }
}
