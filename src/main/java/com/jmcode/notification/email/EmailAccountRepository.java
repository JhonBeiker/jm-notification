package com.jmcode.notification.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    Optional<EmailAccount> findByClientCodeIgnoreCaseAndActiveTrue(String clientCode);

    Optional<EmailAccount> findByIsDefaultTrueAndActiveTrue();

    /** Respaldo cuando no hay cuenta marcada por defecto: evita traer toda la tabla. */
    Optional<EmailAccount> findFirstByActiveTrueOrderByIdAsc();

    boolean existsByClientCodeIgnoreCase(String clientCode);
}
