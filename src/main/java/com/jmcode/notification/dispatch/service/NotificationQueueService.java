package com.jmcode.notification.dispatch.service;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationChannel;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.common.exception.ChannelNotFoundException;
import com.jmcode.notification.common.exception.ConflictException;
import com.jmcode.notification.company.entity.Company;
import com.jmcode.notification.company.service.CompanyScope;
import com.jmcode.notification.config.NotificationProperties;
import com.jmcode.notification.dispatch.entity.NotificationLog;
import com.jmcode.notification.dispatch.repository.NotificationLogRepository;
import com.jmcode.notification.security.model.AuthPrincipal;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cola persistente de notificaciones sobre la tabla {@code notifications} con reintentos
 * y backoff exponencial. Las llamadas a proveedores externos se ejecutan fuera de la
 * transacción de BD para no retener conexiones JDBC mientras responde SMTP/Telegram/GOWA.
 */
@Service
public class NotificationQueueService {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueueService.class);

    private static final int MAX_RECIPIENT_LENGTH = 255;
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_CLIENT_CODE_LENGTH = 64;
    private static final int MAX_TEMPLATE_NAME_LENGTH = 128;
    private static final int MAX_PROVIDER_MESSAGE_ID_LENGTH = 255;
    private static final int MAX_DETAIL_LENGTH = 1000;

    private final Map<ChannelType, NotificationChannel> channels;
    private final NotificationLogRepository repository;
    private final CompanyScope companyScope;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public NotificationQueueService(
            List<NotificationChannel> channelList,
            NotificationLogRepository repository,
            CompanyScope companyScope,
            NotificationProperties properties,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        Map<ChannelType, NotificationChannel> byType = new EnumMap<>(ChannelType.class);
        for (NotificationChannel channel : channelList) {
            byType.put(channel.supports(), channel);
        }
        this.channels = Map.copyOf(byType);
        this.repository = repository;
        this.companyScope = companyScope;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /** Encola una notificación para envío asíncrono con el límite de reintentos configurado. */
    @Transactional
    public NotificationLog enqueue(NotificationRequest request) {
        resolveChannel(request.channel());
        Instant now = Instant.now();

        NotificationLog entry = new NotificationLog();
        entry.setCompany(companyScope.resolveOwner(null));
        entry.setChannel(request.channel());
        entry.setStatus(NotificationStatus.QUEUED);
        entry.setRecipient(truncate(request.to(), MAX_RECIPIENT_LENGTH));
        entry.setSubject(truncate(request.subject(), MAX_SUBJECT_LENGTH));
        entry.setClientCode(truncate(request.effectiveClientCode(), MAX_CLIENT_CODE_LENGTH));
        entry.setTemplateName(truncate(request.templateName(), MAX_TEMPLATE_NAME_LENGTH));
        entry.setDetail("Queued for delivery");
        entry.setPayloadJson(serializePayload(request));
        entry.setAttempts(0);
        entry.setMaxAttempts(Math.max(1, properties.queue().maxAttempts()));
        entry.setNextAttemptAt(now);
        entry.setCreatedAt(now);
        return repository.save(entry);
    }

    /**
     * Reencola manualmente una notificación en estado {@code FAILED} si conserva su payload.
     * Respeta el aislamiento multi-empresa ({@code 403} si es de otra empresa).
     */
    @Transactional
    public NotificationLog retry(Long id) {
        NotificationLog entry = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found with id=" + id));
        companyScope.assertCanAccess(entry.getCompany());

        if (entry.getStatus() != NotificationStatus.FAILED) {
            throw new ConflictException("Only FAILED notifications can be retried (current status: " + entry.getStatus() + ")");
        }
        if (!StringUtils.hasText(entry.getPayloadJson())) {
            throw new ConflictException("Notification id=" + id + " has no stored payload available for retry");
        }

        entry.setStatus(NotificationStatus.QUEUED);
        entry.setMaxAttempts(Math.max(entry.getMaxAttempts(), entry.getAttempts() + 1));
        entry.setNextAttemptAt(Instant.now());
        entry.setDetail("Queued for manual retry");
        return repository.save(entry);
    }

    /**
     * Recupera trabajos atascados en {@code PROCESSING}, reclama hasta {@code batchSize}
     * notificaciones vencidas y las entrega. Devuelve cuántas se procesaron.
     */
    public int processPendingBatch() {
        recoverStaleProcessing();
        List<Long> claimedIds = claimDueJobs();
        for (Long id : claimedIds) {
            deliver(id);
        }
        return claimedIds.size();
    }

    /**
     * Si el proceso cayó a mitad de un envío, las filas que llevan más de
     * {@code processingTimeout} en {@code PROCESSING} vuelven a {@code QUEUED}.
     */
    void recoverStaleProcessing() {
        Instant cutoff = Instant.now().minus(properties.queue().processingTimeout());
        transactionTemplate.executeWithoutResult(status -> {
            List<NotificationLog> stale = repository.findByStatusAndLastAttemptAtBefore(NotificationStatus.PROCESSING, cutoff);
            if (stale.isEmpty()) {
                return;
            }
            Instant now = Instant.now();
            for (NotificationLog entry : stale) {
                entry.setStatus(NotificationStatus.QUEUED);
                entry.setNextAttemptAt(now);
                entry.setDetail("Recovered from stale PROCESSING state");
            }
            repository.saveAll(stale);
            log.warn("Recovered {} stale PROCESSING notification(s) back to QUEUED", stale.size());
        });
    }

    List<Long> claimDueJobs() {
        int batchSize = Math.max(1, properties.queue().batchSize());
        List<Long> ids = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<NotificationLog> due = repository
                    .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                            NotificationStatus.QUEUED, now, PageRequest.of(0, batchSize));
            if (due.isEmpty()) {
                return List.of();
            }
            for (NotificationLog entry : due) {
                entry.setStatus(NotificationStatus.PROCESSING);
                entry.setLastAttemptAt(now);
            }
            repository.saveAll(due);
            return due.stream().map(NotificationLog::getId).toList();
        });
        return ids == null ? List.of() : ids;
    }

    void deliver(Long id) {
        NotificationLog snapshot = repository.findById(id).orElse(null);
        if (snapshot == null || snapshot.getStatus() != NotificationStatus.PROCESSING) {
            return;
        }

        NotificationRequest request;
        try {
            request = deserializePayload(snapshot.getPayloadJson());
        } catch (RuntimeException ex) {
            log.error("Failed to deserialize payload for queued notification id={}", id, ex);
            markTerminalFailure(id, "Corrupted notification payload: " + ex.getMessage());
            return;
        }

        NotificationChannel channel;
        try {
            channel = resolveChannel(snapshot.getChannel());
        } catch (ChannelNotFoundException ex) {
            markTerminalFailure(id, ex.getMessage());
            return;
        }

        NotificationResult result;
        boolean retryable = true;
        try {
            result = sendWithCompanyScope(snapshot.getCompany(), channel, request);
        } catch (AccessDeniedException ex) {
            // Un clientCode de otra empresa no se arregla reintentando: fallo definitivo.
            result = NotificationResult.failed(snapshot.getChannel(), snapshot.getRecipient(), ex.getMessage());
            retryable = false;
        } catch (RuntimeException ex) {
            log.error("Unexpected error delivering queued notification id={}", id, ex);
            result = NotificationResult.failed(snapshot.getChannel(), snapshot.getRecipient(), ex.getMessage());
        }

        finalizeAttempt(id, result, retryable);
    }

    private NotificationResult sendWithCompanyScope(Company company, NotificationChannel channel, NotificationRequest request) {
        if (company == null) {
            return channel.send(request);
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext scoped = SecurityContextHolder.createEmptyContext();
        AuthPrincipal principal = new AuthPrincipal(0L, "notification-worker", Role.ADMIN, company.getId());
        scoped.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        SecurityContextHolder.setContext(scoped);
        try {
            return channel.send(request);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void finalizeAttempt(Long id, NotificationResult result, boolean retryable) {
        transactionTemplate.executeWithoutResult(txStatus -> {
            NotificationLog entry = repository.findById(id).orElse(null);
            if (entry == null) {
                return;
            }
            Instant now = Instant.now();
            int nextAttemptNumber = entry.getAttempts() + 1;
            entry.setAttempts(nextAttemptNumber);
            entry.setLastAttemptAt(now);
            entry.setProviderMessageId(truncate(result.providerMessageId(), MAX_PROVIDER_MESSAGE_ID_LENGTH));
            entry.setDetail(truncate(result.detail(), MAX_DETAIL_LENGTH));

            if (result.status() == NotificationStatus.SENT || result.status() == NotificationStatus.SKIPPED) {
                entry.setStatus(result.status());
                entry.setNextAttemptAt(null);
                // Ya no hace falta conservar el payload (ni posibles adjuntos base64) una vez entregada.
                entry.setPayloadJson(null);
            } else if (retryable && nextAttemptNumber < entry.getMaxAttempts()) {
                Duration backoff = computeBackoff(nextAttemptNumber);
                entry.setStatus(NotificationStatus.QUEUED);
                entry.setNextAttemptAt(now.plus(backoff));
                log.warn("Notification id={} failed on attempt {}/{}; retrying in {}: {}",
                        id, nextAttemptNumber, entry.getMaxAttempts(), backoff, result.detail());
            } else {
                entry.setStatus(NotificationStatus.FAILED);
                entry.setNextAttemptAt(null);
                log.error("Notification id={} permanently FAILED after {}/{} attempt(s): {}",
                        id, nextAttemptNumber, entry.getMaxAttempts(), result.detail());
            }
            repository.save(entry);
        });
    }

    private void markTerminalFailure(Long id, String detail) {
        transactionTemplate.executeWithoutResult(txStatus -> repository.findById(id).ifPresent(entry -> {
            entry.setAttempts(entry.getAttempts() + 1);
            entry.setLastAttemptAt(Instant.now());
            entry.setStatus(NotificationStatus.FAILED);
            entry.setNextAttemptAt(null);
            entry.setDetail(truncate(detail, MAX_DETAIL_LENGTH));
            repository.save(entry);
        }));
    }

    /** Backoff exponencial: initialBackoff * 2^(failedAttempt - 1), acotado por maxBackoff. */
    Duration computeBackoff(int failedAttempt) {
        Duration initial = properties.queue().initialBackoff();
        Duration max = properties.queue().maxBackoff();
        long initialMillis = Math.max(0L, initial.toMillis());
        long maxMillis = Math.max(initialMillis, max.toMillis());
        if (initialMillis == 0L) {
            return Duration.ZERO;
        }
        int shift = Math.min(Math.max(0, failedAttempt - 1), 30);
        long multiplier = 1L << shift;
        long candidate = initialMillis > maxMillis / multiplier ? maxMillis : initialMillis * multiplier;
        return Duration.ofMillis(Math.min(candidate, maxMillis));
    }

    private NotificationChannel resolveChannel(ChannelType type) {
        NotificationChannel channel = channels.get(type);
        if (channel == null) {
            throw new ChannelNotFoundException(type);
        }
        return channel;
    }

    private String serializePayload(NotificationRequest request) {
        return objectMapper.writeValueAsString(request);
    }

    private NotificationRequest deserializePayload(String json) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalStateException("Empty payload");
        }
        return objectMapper.readValue(json, NotificationRequest.class);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
