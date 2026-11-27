package com.jmcode.notification.dispatch.service;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.company.entity.Company;
import com.jmcode.notification.company.service.CompanyScope;
import com.jmcode.notification.config.NotificationProperties;
import com.jmcode.notification.dispatch.entity.NotificationLog;
import com.jmcode.notification.dispatch.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationQueueServiceTest {

    private NotificationLogRepository repository;
    private CompanyScope companyScope;
    private ObjectMapper objectMapper;
    private TransactionTemplate txTemplate;
    private NotificationProperties properties;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationLogRepository.class);
        companyScope = mock(CompanyScope.class);
        objectMapper = new ObjectMapper();
        txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(inv -> {
            Consumer<TransactionStatus> cb = inv.getArgument(0);
            cb.accept(mock(TransactionStatus.class));
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        NotificationProperties.Queue queue = new NotificationProperties.Queue(
                true, Duration.ofSeconds(5), 20, 3,
                Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(5)
        );
        properties = new NotificationProperties(null, null, null, queue);
    }

    @Test
    void computesExponentialBackoffCappedAtMax() {
        NotificationQueueService service = newService(alwaysSentChannel());

        assertEquals(Duration.ofSeconds(30), service.computeBackoff(1));
        assertEquals(Duration.ofSeconds(60), service.computeBackoff(2));
        assertEquals(Duration.ofMinutes(2), service.computeBackoff(3));
        assertEquals(Duration.ofMinutes(2), service.computeBackoff(4));
    }

    @Test
    void retriesWithBackoffUntilMaxAttemptsThenFailsPermanently() {
        AtomicInteger calls = new AtomicInteger();
        NotificationChannel flaky = new NotificationChannel() {
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
                calls.incrementAndGet();
                return NotificationResult.failed(ChannelType.EMAIL, request.to(), "smtp timeout");
            }
        };
        NotificationQueueService service = newService(flaky);
        NotificationLog entry = processingEntry(10L, 3);
        when(repository.findById(10L)).thenReturn(Optional.of(entry));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Attempt 1 -> re-queued with 30s backoff
        service.deliver(10L);
        assertEquals(1, entry.getAttempts());
        assertEquals(NotificationStatus.QUEUED, entry.getStatus());
        assertNotNull(entry.getNextAttemptAt());
        assertNotNull(entry.getPayloadJson());

        // Attempt 2 -> re-queued with 60s backoff
        entry.setStatus(NotificationStatus.PROCESSING);
        service.deliver(10L);
        assertEquals(2, entry.getAttempts());
        assertEquals(NotificationStatus.QUEUED, entry.getStatus());
        assertNotNull(entry.getNextAttemptAt());

        // Attempt 3 -> permanently FAILED, payload preserved for manual retry
        entry.setStatus(NotificationStatus.PROCESSING);
        service.deliver(10L);
        assertEquals(3, calls.get());
        assertEquals(3, entry.getAttempts());
        assertEquals(NotificationStatus.FAILED, entry.getStatus());
        assertNull(entry.getNextAttemptAt());
        assertNotNull(entry.getPayloadJson());
    }

    @Test
    void accessDeniedIsNonRetryable() {
        NotificationChannel denied = new NotificationChannel() {
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
                throw new AccessDeniedException("clientCode belongs to another company");
            }
        };
        NotificationQueueService service = newService(denied);
        NotificationLog entry = processingEntry(11L, 3);
        when(repository.findById(11L)).thenReturn(Optional.of(entry));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deliver(11L);

        assertEquals(1, entry.getAttempts());
        assertEquals(NotificationStatus.FAILED, entry.getStatus());
        assertNull(entry.getNextAttemptAt());
        assertTrue(entry.getDetail().contains("another company"));
    }

    @Test
    void successfulDeliveryClearsPayloadAndMarksSent() {
        NotificationQueueService service = newService(alwaysSentChannel());
        NotificationLog entry = processingEntry(12L, 3);
        when(repository.findById(12L)).thenReturn(Optional.of(entry));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deliver(12L);

        assertEquals(1, entry.getAttempts());
        assertEquals(NotificationStatus.SENT, entry.getStatus());
        assertEquals("msg-ok", entry.getProviderMessageId());
        assertNull(entry.getNextAttemptAt());
        assertNull(entry.getPayloadJson());
    }

    @Test
    void recoversStaleProcessingRowsBackToQueued() {
        NotificationQueueService service = newService(alwaysSentChannel());
        NotificationLog stale = processingEntry(13L, 3);
        stale.setLastAttemptAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(repository.findByStatusAndLastAttemptAtBefore(eq(NotificationStatus.PROCESSING), any()))
                .thenReturn(List.of(stale));

        service.recoverStaleProcessing();

        assertEquals(NotificationStatus.QUEUED, stale.getStatus());
        assertNotNull(stale.getNextAttemptAt());
    }

    private NotificationQueueService newService(NotificationChannel channel) {
        return new NotificationQueueService(
                List.of(channel), repository, companyScope, properties, objectMapper, txTemplate
        );
    }

    private NotificationLog processingEntry(Long id, int maxAttempts) {
        Company company = new Company();
        company.setId(1L);
        company.setCode("acme");

        NotificationRequest req = new NotificationRequest(
                ChannelType.EMAIL, "user@example.com", "Hi", "Body", null, Map.of(), null, Map.of(), List.of()
        );
        NotificationLog entry = new NotificationLog();
        entry.setId(id);
        entry.setCompany(company);
        entry.setChannel(ChannelType.EMAIL);
        entry.setStatus(NotificationStatus.PROCESSING);
        entry.setRecipient("user@example.com");
        entry.setAttempts(0);
        entry.setMaxAttempts(maxAttempts);
        entry.setPayloadJson(objectMapper.writeValueAsString(req));
        return entry;
    }

    private static NotificationChannel alwaysSentChannel() {
        return new NotificationChannel() {
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
                return NotificationResult.sent(ChannelType.EMAIL, request.to(), "msg-ok");
            }
        };
    }
}
