package com.jmcode.notification.web;

import com.jmcode.notification.telegram.TelegramSubscriber;
import com.jmcode.notification.telegram.TelegramSubscriberRepository;
import com.jmcode.notification.telegram.TelegramSubscriberService;
import com.jmcode.notification.web.dto.LinkExternalUserRequestDto;
import com.jmcode.notification.web.dto.TelegramSubscriberResponseDto;
import com.jmcode.notification.web.error.SubscriberNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
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
public class TelegramSubscriberController {

    private final TelegramSubscriberService subscriberService;
    private final TelegramSubscriberRepository repository;

    public TelegramSubscriberController(
            TelegramSubscriberService subscriberService,
            TelegramSubscriberRepository repository
    ) {
        this.subscriberService = subscriberService;
        this.repository = repository;
    }

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
        return repository.findByChatId(chatId)
                .map(TelegramSubscriberResponseDto::from)
                .orElseThrow(() -> new SubscriberNotFoundException("Subscriber not found for chat_id=" + chatId));
    }

    @PutMapping("/link")
    @Transactional
    @Operation(summary = "Vincular externalUserId", description = "Asocia un id de tu sistema al chat_id de Telegram.")
    public TelegramSubscriberResponseDto link(@Valid @RequestBody LinkExternalUserRequestDto request) {
        TelegramSubscriber subscriber = repository.findByChatId(request.chatId())
                .orElseThrow(() -> new SubscriberNotFoundException(
                        "Subscriber not found for chat_id=" + request.chatId() + ". User must /start first."
                ));
        subscriber.setExternalUserId(request.externalUserId().trim());
        return TelegramSubscriberResponseDto.from(repository.save(subscriber));
    }
}
