package com.jmcode.notification.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    Optional<EmailAccount> findByClientCodeIgnoreCaseAndActiveTrue(String clientCode);

    Optional<EmailAccount> findByIsDefaultTrueAndActiveTrue();

    /** Respaldo cuando no hay cuenta marcada por defecto: evita traer toda la tabla. */
    Optional<EmailAccount> findFirstByActiveTrueOrderByIdAsc();

    /** Mismo orden de resolución, pero dentro de la empresa del llamante acotado. */
    Optional<EmailAccount> findByCompanyIdAndIsDefaultTrueAndActiveTrue(Long companyId);

    Optional<EmailAccount> findFirstByCompanyIdAndActiveTrueOrderByIdAsc(Long companyId);

    boolean existsByClientCodeIgnoreCase(String clientCode);

    List<EmailAccount> findAllByCompanyId(Long companyId);

    long countByCompanyId(Long companyId);
}
