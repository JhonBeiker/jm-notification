package com.jmcode.notification.email;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailAccountService {

    private final EmailAccountRepository repository;
    private final EmailAccountManager accountManager;
    private final PasswordEncryptor passwordEncryptor;

    @Transactional(readOnly = true)
    public EmailAccount resolveAccount(String clientCode) {
        if (StringUtils.hasText(clientCode)) {
            return repository.findByClientCodeIgnoreCaseAndActiveTrue(clientCode.trim())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Active email account not found for client code: " + clientCode));
        }
        return repository.findByIsDefaultTrueAndActiveTrue()
                .or(repository::findFirstByActiveTrueOrderByIdAsc)
                .orElseThrow(() -> new EntityNotFoundException("No active default email account configured in database"));
    }

    @Transactional(readOnly = true)
    public List<EmailAccount> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public EmailAccount getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Email account not found with id=" + id));
    }

    @Transactional
    public EmailAccount save(EmailAccount account) {
        if (account.getId() == null && repository.existsByClientCodeIgnoreCase(account.getClientCode())) {
            throw new IllegalArgumentException("Email account already exists for clientCode: " + account.getClientCode());
        }
        account.setPassword(passwordEncryptor.encrypt(account.getPassword()));
        if (account.isDefault()) {
            clearPreviousDefault(account);
        }
        EmailAccount saved = repository.save(account);
        // El sender cacheado lleva host/puerto/credenciales embebidos: hay que descartarlo.
        accountManager.evict(saved.getClientCode());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        EmailAccount account = getById(id);
        repository.delete(account);
        accountManager.evict(account.getClientCode());
    }

    private void clearPreviousDefault(EmailAccount account) {
        repository.findByIsDefaultTrueAndActiveTrue()
                .filter(existing -> !existing.getId().equals(account.getId()))
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    repository.save(existing);
                });
    }
}
