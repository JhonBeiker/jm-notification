package com.jmcode.notification.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    /** Plantillas anteriores a las empresas: la cuenta que las usa tampoco tiene empresa. */
    Optional<EmailTemplate> findByCompanyIsNullAndNameIgnoreCase(String name);

    List<EmailTemplate> findAllByCompanyId(Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIsNullAndNameIgnoreCase(String name);

    long countByCompanyId(Long companyId);
}
