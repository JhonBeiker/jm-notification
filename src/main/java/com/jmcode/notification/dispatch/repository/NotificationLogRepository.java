package com.jmcode.notification.dispatch.repository;

import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.dispatch.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long>,
        JpaSpecificationExecutor<NotificationLog> {

    Page<NotificationLog> findAllByCompanyId(Long companyId, Pageable pageable);

    long countByCompanyId(Long companyId);

    List<NotificationLog> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
            NotificationStatus status, Instant now, Pageable pageable);

    List<NotificationLog> findByStatusAndLastAttemptAtBefore(NotificationStatus status, Instant cutoff);
}
