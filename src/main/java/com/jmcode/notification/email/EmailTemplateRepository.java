package com.jmcode.notification.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByTenantIdAndName(String tenantId, String name);

    Optional<EmailTemplate> findByTenantIdAndActiveTrue(String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);
}