package com.jmcode.notification.telegram;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true)
    public TelegramBotAccount resolveAccount(String clientCode) {
        if (StringUtils.hasText(clientCode)) {
            return repository.findByClientCodeIgnoreCaseAndActiveTrue(clientCode.trim())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Active telegram bot account not found for client code: " + clientCode));
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

    @Transactional(readOnly = true)
    public List<TelegramBotAccount> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<TelegramBotAccount> listActive() {
        return repository.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
    public TelegramBotAccount getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Telegram bot account not found with id=" + id));
    }

    @Transactional
    public TelegramBotAccount save(TelegramBotAccount account) {
        if (account.getId() == null && repository.existsByClientCodeIgnoreCase(account.getClientCode())) {
            throw new IllegalArgumentException(
                    "Telegram bot account already exists for clientCode: " + account.getClientCode());
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
