package com.jmcode.notification.dispatch.repository;

import com.jmcode.notification.dispatch.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long>,
        JpaSpecificationExecutor<NotificationLog> {

    Page<NotificationLog> findAllByCompanyId(Long companyId, Pageable pageable);

    long countByCompanyId(Long companyId);
}
