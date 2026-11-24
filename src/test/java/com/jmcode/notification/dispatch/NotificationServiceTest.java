package com.jmcode.notification.dispatch;

import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.dispatch.dto.NotificationRequestDto;
import com.jmcode.notification.common.NotificationSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {

    private NotificationService service;

    @BeforeEach
    void setUp() {
        NotificationChannel email = new NotificationChannel() {
            @Override
            public ChannelType supports() {
                return ChannelType.EMAIL;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public NotificationResult send(NotificationRequest request) {
                if ("fail@example.com".equals(request.to())) {
                    return NotificationResult.failed(ChannelType.EMAIL, request.to(), "smtp error");
                }
                return NotificationResult.sent(ChannelType.EMAIL, request.to(), "msg-1");
            }
        };
        service = new NotificationService(List.of(email));
    }

    @Test
    void sendSuccess() {
        NotificationResult result = service.send(new NotificationRequestDto(
                ChannelType.EMAIL, "ok@example.com", "Hi", "Body", null, Map.of(), null, Map.of(), List.of()
        ));
        assertEquals(NotificationStatus.SENT, result.status());
        assertEquals("msg-1", result.providerMessageId());
    }

    @Test
    void sendFailureThrows() {
        NotificationRequestDto dto = new NotificationRequestDto(
                ChannelType.EMAIL, "fail@example.com", "Hi", "Body", null, Map.of(), null, Map.of(), List.of()
        );
        assertThrows(NotificationSendException.class, () -> service.send(dto));
    }

    @Test
    void channelStatus() {
        Map<ChannelType, Boolean> status = service.channelStatus();
        assertTrue(status.get(ChannelType.EMAIL));
    }
}
