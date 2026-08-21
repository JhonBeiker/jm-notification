package com.jmcode.notification.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    Optional<EmailAccount> findByClientCodeIgnoreCaseAndActiveTrue(String clientCode);

    Optional<EmailAccount> findByIsDefaultTrueAndActiveTrue();

    boolean existsByClientCodeIgnoreCase(String clientCode);
}
