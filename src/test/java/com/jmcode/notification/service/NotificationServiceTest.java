package com.jmcode.notification.service;

import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.domain.ChannelType;
import com.jmcode.notification.domain.NotificationRequest;
import com.jmcode.notification.domain.NotificationResult;
import com.jmcode.notification.domain.NotificationStatus;
import com.jmcode.notification.web.dto.NotificationRequestDto;
import com.jmcode.notification.web.error.NotificationSendException;
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
                ChannelType.EMAIL, "ok@example.com", "Hi", "Body", null, Map.of()
        ));
        assertEquals(NotificationStatus.SENT, result.status());
        assertEquals("msg-1", result.providerMessageId());
    }

    @Test
    void sendFailureThrows() {
        NotificationRequestDto dto = new NotificationRequestDto(
                ChannelType.EMAIL, "fail@example.com", "Hi", "Body", null, null
        );
        assertThrows(NotificationSendException.class, () -> service.send(dto));
    }

    @Test
    void channelStatus() {
        Map<ChannelType, Boolean> status = service.channelStatus();
        assertTrue(status.get(ChannelType.EMAIL));
    }
}
