package com.jmcode.notification.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramBotAccountRepository extends JpaRepository<TelegramBotAccount, Long> {

    Optional<TelegramBotAccount> findByClientCodeIgnoreCaseAndActiveTrue(String clientCode);

    Optional<TelegramBotAccount> findByIsDefaultTrueAndActiveTrue();

    List<TelegramBotAccount> findAllByActiveTrue();

    Optional<TelegramBotAccount> findFirstByActiveTrueOrderByIdAsc();

    /** Mismo orden de resolución, pero dentro de la empresa del llamante acotado. */
    Optional<TelegramBotAccount> findByCompanyIdAndIsDefaultTrueAndActiveTrue(Long companyId);

    Optional<TelegramBotAccount> findFirstByCompanyIdAndActiveTrueOrderByIdAsc(Long companyId);

    boolean existsByClientCodeIgnoreCase(String clientCode);

    Optional<TelegramBotAccount> findByWebhookSecretAndActiveTrue(String webhookSecret);

    /** Si alguna cuenta activa define secret, el webhook deja de aceptar peticiones sin él. */
    @Query("select count(a) > 0 from TelegramBotAccount a "
            + "where a.active = true and a.webhookSecret is not null and a.webhookSecret <> ''")
    boolean existsActiveWithWebhookSecret();

    long countByActiveTrue();

    List<TelegramBotAccount> findAllByCompanyId(Long companyId);

    long countByCompanyId(Long companyId);
}
