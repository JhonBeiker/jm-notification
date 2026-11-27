package com.jmcode.notification.dispatch.service;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.company.service.CompanyScope;
import com.jmcode.notification.dispatch.dto.NotificationLogFilter;
import com.jmcode.notification.dispatch.dto.NotificationStatsResponseDto;
import com.jmcode.notification.dispatch.entity.NotificationLog;
import com.jmcode.notification.dispatch.repository.NotificationLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationLogService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_RECIPIENT_LENGTH = 255;
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_CLIENT_CODE_LENGTH = 64;
    private static final int MAX_TEMPLATE_NAME_LENGTH = 128;
    private static final int MAX_PROVIDER_MESSAGE_ID_LENGTH = 255;
    private static final int MAX_DETAIL_LENGTH = 1000;

    private final NotificationLogRepository repository;
    private final CompanyScope companyScope;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationLog record(NotificationRequest request, NotificationResult result) {
        Instant timestamp = result.timestamp() != null ? result.timestamp() : Instant.now();
        NotificationLog log = new NotificationLog();
        log.setCompany(companyScope.resolveOwner(null));
        log.setChannel(result.channel());
        log.setStatus(result.status());
        log.setRecipient(truncate(result.to(), MAX_RECIPIENT_LENGTH));
        log.setSubject(truncate(request.subject(), MAX_SUBJECT_LENGTH));
        log.setClientCode(truncate(request.effectiveClientCode(), MAX_CLIENT_CODE_LENGTH));
        log.setTemplateName(truncate(request.templateName(), MAX_TEMPLATE_NAME_LENGTH));
        log.setProviderMessageId(truncate(result.providerMessageId(), MAX_PROVIDER_MESSAGE_ID_LENGTH));
        log.setDetail(truncate(result.detail(), MAX_DETAIL_LENGTH));
        log.setAttempts(1);
        log.setMaxAttempts(1);
        log.setLastAttemptAt(timestamp);
        log.setCreatedAt(timestamp);
        if (result.status() == NotificationStatus.FAILED) {
            log.setPayloadJson(objectMapper.writeValueAsString(request));
        }
        return repository.save(log);
    }

    /** El ADMIN de una empresa sólo ve las notificaciones de su empresa; el filtro companyId que pida se ignora. */
    @Transactional(readOnly = true)
    public Page<NotificationLog> list(NotificationLogFilter filter, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        validateDateRange(filter.from(), filter.until());

        Long effectiveCompanyId = resolveCompanyFilter(filter.companyId());
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Specification<NotificationLog> spec = buildSpec(effectiveCompanyId, filter);
        return repository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public NotificationLog getById(Long id) {
        NotificationLog log = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found with id=" + id));
        companyScope.assertCanAccess(log.getCompany());
        return log;
    }

    /** Totales por estado y por canal, respetando el ámbito de empresa y un rango de fechas opcional. */
    @Transactional(readOnly = true)
    public NotificationStatsResponseDto stats(Long requestedCompanyId, Instant from, Instant until) {
        validateDateRange(from, until);
        Long effectiveCompanyId = resolveCompanyFilter(requestedCompanyId);

        Map<NotificationStatus, Long> byStatus = new EnumMap<>(NotificationStatus.class);
        for (NotificationStatus status : NotificationStatus.values()) {
            byStatus.put(status, 0L);
        }
        long total = 0L;
        for (Tuple row : countGroupedBy("status", effectiveCompanyId, from, until)) {
            NotificationStatus status = row.get(0, NotificationStatus.class);
            long count = row.get(1, Long.class);
            byStatus.put(status, count);
            total += count;
        }

        Map<ChannelType, Long> byChannel = new EnumMap<>(ChannelType.class);
        for (ChannelType channel : ChannelType.values()) {
            byChannel.put(channel, 0L);
        }
        for (Tuple row : countGroupedBy("channel", effectiveCompanyId, from, until)) {
            ChannelType channel = row.get(0, ChannelType.class);
            long count = row.get(1, Long.class);
            byChannel.put(channel, count);
        }

        return new NotificationStatsResponseDto(total, byStatus, byChannel);
    }

    private Long resolveCompanyFilter(Long requestedCompanyId) {
        Long scoped = companyScope.filterCompanyId();
        return scoped != null ? scoped : requestedCompanyId;
    }

    private static void validateDateRange(Instant from, Instant until) {
        if (from != null && until != null && from.isAfter(until)) {
            throw new IllegalArgumentException("'from' must not be after 'until'");
        }
    }

    private static Specification<NotificationLog> buildSpec(Long companyId, NotificationLogFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (companyId != null) {
                predicates.add(cb.equal(root.get("company").get("id"), companyId));
            }
            if (filter.channel() != null) {
                predicates.add(cb.equal(root.get("channel"), filter.channel()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (StringUtils.hasText(filter.recipient())) {
                String pattern = "%" + filter.recipient().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("recipient")), pattern));
            }
            if (StringUtils.hasText(filter.clientCode())) {
                predicates.add(cb.equal(
                        cb.lower(root.get("clientCode")),
                        filter.clientCode().trim().toLowerCase(Locale.ROOT)
                ));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.until() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.until()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<Tuple> countGroupedBy(String field, Long companyId, Instant from, Instant until) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<NotificationLog> root = query.from(NotificationLog.class);

        List<Predicate> predicates = new ArrayList<>();
        if (companyId != null) {
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (until != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), until));
        }

        query.multiselect(root.get(field), cb.count(root));
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }
        query.groupBy(root.get(field));
        return entityManager.createQuery(query).getResultList();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
