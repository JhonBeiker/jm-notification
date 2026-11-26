package com.jmcode.notification.telegram.service;

import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import org.junit.jupiter.api.Test;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import org.mockito.ArgumentCaptor;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import org.mockito.Mock;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import java.util.Optional;

import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import static org.mockito.ArgumentMatchers.any;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import static org.mockito.ArgumentMatchers.eq;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import static org.mockito.Mockito.verify;
import com.jmcode.notification.telegram.client.TelegramBotClient;
import com.jmcode.notification.telegram.dto.TelegramUpdatePayload;
import com.jmcode.notification.telegram.entity.TelegramBotAccount;
import com.jmcode.notification.telegram.entity.TelegramSubscriber;
import com.jmcode.notification.telegram.repository.TelegramSubscriberRepository;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TelegramSubscriberServiceTest {

    @Mock
    private TelegramSubscriberRepository repository;

    @Mock
    private TelegramBotAccountManager accountManager;

    @Mock
    private TelegramBotClient botClient;

    @Mock
    private TelegramBotAccount account;

    private TelegramSubscriberService service;

    @BeforeEach
    void setUp() {
        when(account.getWelcomeMessage()).thenReturn("welcome");
        when(account.getGoodbyeMessage()).thenReturn("goodbye");
        when(accountManager.getClient(account)).thenReturn(botClient);
        
        service = new TelegramSubscriberService(repository, accountManager);
    }

    @Test
    void startActivatesSubscriberAndSendsWelcome() {
        when(repository.findByChatId(42L)).thenReturn(Optional.empty());
        when(repository.save(any(TelegramSubscriber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TelegramUpdatePayload update = new TelegramUpdatePayload(
                1L,
                new TelegramUpdatePayload.TelegramMessage(
                        10L,
                        new TelegramUpdatePayload.TelegramUser(7L, false, "Ana", null, "ana", "es"),
                        new TelegramUpdatePayload.TelegramChat(42L, "private", null, null, "Ana", null),
                        1L,
                        "/start user-99"
                ),
                null,
                null
        );

        service.handleUpdate(update, account);

        ArgumentCaptor<TelegramSubscriber> captor = ArgumentCaptor.forClass(TelegramSubscriber.class);
        verify(repository).save(captor.capture());
        TelegramSubscriber saved = captor.getValue();
        assertEquals(42L, saved.getChatId());
        assertEquals("user-99", saved.getExternalUserId());
        assertTrue(saved.isActive());
        verify(botClient).sendText(eq(42L), eq("welcome"));
    }

    @Test
    void stopDeactivatesSubscriber() {
        TelegramSubscriber existing = new TelegramSubscriber();
        existing.setChatId(42L);
        existing.setActive(true);
        when(repository.findByChatId(42L)).thenReturn(Optional.of(existing));
        when(repository.save(any(TelegramSubscriber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TelegramUpdatePayload update = new TelegramUpdatePayload(
                2L,
                new TelegramUpdatePayload.TelegramMessage(
                        11L,
                        new TelegramUpdatePayload.TelegramUser(7L, false, "Ana", null, "ana", "es"),
                        new TelegramUpdatePayload.TelegramChat(42L, "private", null, null, "Ana", null),
                        1L,
                        "/stop"
                ),
                null,
                null
        );

        service.handleUpdate(update, account);

        assertFalse(existing.isActive());
        verify(botClient).sendText(eq(42L), eq("goodbye"));
    }
}
