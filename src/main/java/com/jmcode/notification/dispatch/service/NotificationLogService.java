package com.jmcode.notification.dispatch.service;

import com.jmcode.notification.channel.NotificationRequest;
import com.jmcode.notification.channel.NotificationResult;
import com.jmcode.notification.company.service.CompanyScope;
import com.jmcode.notification.dispatch.entity.NotificationLog;
import com.jmcode.notification.dispatch.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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

    @Transactional
    public NotificationLog record(NotificationRequest request, NotificationResult result) {
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
        log.setCreatedAt(result.timestamp() != null ? result.timestamp() : Instant.now());
        return repository.save(log);
    }

    /** El ADMIN de una empresa sólo ve las notificaciones de su empresa; el filtro que pida se ignora. */
    @Transactional(readOnly = true)
    public Page<NotificationLog> list(Long requestedCompanyId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Long scoped = companyScope.filterCompanyId();
        if (scoped != null) {
            return repository.findAllByCompanyId(scoped, pageable);
        }
        return requestedCompanyId == null
                ? repository.findAll(pageable)
                : repository.findAllByCompanyId(requestedCompanyId, pageable);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
