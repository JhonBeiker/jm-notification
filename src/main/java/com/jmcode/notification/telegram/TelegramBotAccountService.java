package com.jmcode.notification.telegram;

import com.jmcode.notification.company.CompanyScope;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TelegramBotAccountService {

    private final TelegramBotAccountRepository repository;
    private final TelegramBotAccountManager accountManager;
    private final CompanyScope companyScope;

    /**
     * Resolución de envío / webhook. La API Key (y el JWT de un ADMIN) acotan a su empresa:
     * un {@code clientCode} ajeno se rechaza con 403 y, sin {@code clientCode}, el bot por
     * defecto que se busca es el de esa empresa. El webhook entrante no lleva identidad,
     * así que sigue resolviendo por el secret contra cualquier cuenta.
     */
    @Transactional(readOnly = true)
    public TelegramBotAccount resolveAccount(String clientCode) {
        if (StringUtils.hasText(clientCode)) {
            TelegramBotAccount account = repository.findByClientCodeIgnoreCaseAndActiveTrue(clientCode.trim())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Active telegram bot account not found for client code: " + clientCode));
            companyScope.assertCanAccess(account.getCompany());
            return account;
        }
        Long companyId = companyScope.filterCompanyId();
        if (companyId != null) {
            return repository.findByCompanyIdAndIsDefaultTrueAndActiveTrue(companyId)
                    .or(() -> repository.findFirstByCompanyIdAndActiveTrueOrderByIdAsc(companyId))
                    .orElseThrow(() -> new EntityNotFoundException(
                            "No active telegram bot account configured for the company of this caller"));
        }
        return repository.findByIsDefaultTrueAndActiveTrue()
                .or(repository::findFirstByActiveTrueOrderByIdAsc)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active default telegram bot account configured in database"));
    }

    @Transactional(readOnly = true)
    public Optional<TelegramBotAccount> findByWebhookSecret(String webhookSecret) {
        if (!StringUtils.hasText(webhookSecret)) {
            return Optional.empty();
        }
        return repository.findByWebhookSecretAndActiveTrue(webhookSecret.trim());
    }

    /**
     * {@code true} si alguna cuenta activa tiene secret configurado. En ese caso el webhook
     * exige un secret válido y deja de caer a la cuenta por defecto.
     */
    @Transactional(readOnly = true)
    public boolean webhookSecretRequired() {
        return repository.existsActiveWithWebhookSecret();
    }

    /** Administración: el ADMIN de una empresa sólo ve los bots de la suya. */
    @Transactional(readOnly = true)
    public List<TelegramBotAccount> listAll() {
        Long companyId = companyScope.filterCompanyId();
        return companyId == null ? repository.findAll() : repository.findAllByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public List<TelegramBotAccount> listActive() {
        return repository.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
    public TelegramBotAccount getById(Long id) {
        TelegramBotAccount account = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Telegram bot account not found with id=" + id));
        companyScope.assertCanAccess(account.getCompany());
        return account;
    }

    @Transactional
    public TelegramBotAccount save(TelegramBotAccount account, Long requestedCompanyId) {
        if (account.getId() == null && repository.existsByClientCodeIgnoreCase(account.getClientCode())) {
            throw new IllegalArgumentException(
                    "Telegram bot account already exists for clientCode: " + account.getClientCode());
        }
        account.setCompany(companyScope.resolveOwner(requestedCompanyId));
        if (account.isDefault() && !companyScope.unrestricted()) {
            // El bot por defecto es global: lo usa el envío sin clientCode y el webhook sin secret.
            throw new AccessDeniedException("Only a SUPER_ADMIN can set the global default telegram bot account");
        }
        if (account.isDefault()) {
            clearPreviousDefault(account);
        }
        TelegramBotAccount saved = repository.save(account);
        accountManager.evict(saved.getClientCode());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        TelegramBotAccount account = getById(id);
        repository.delete(account);
        accountManager.evict(account.getClientCode());
    }

    private void clearPreviousDefault(TelegramBotAccount account) {
        repository.findByIsDefaultTrueAndActiveTrue()
                .filter(existing -> !existing.getId().equals(account.getId()))
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    repository.save(existing);
                });
    }
}
