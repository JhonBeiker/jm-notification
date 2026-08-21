package com.jmcode.notification.email;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class EmailAccountService implements CommandLineRunner {

    private final EmailAccountRepository repository;

    public EmailAccountService(EmailAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() == 0) {
            EmailAccount account = new EmailAccount();
            account.setClientCode("default");
            account.setName("Default System Email Account");
            account.setHost("smtp.gmail.com");
            account.setPort(587);
            account.setUsername("");
            account.setPassword("");
            account.setFromAddress("");
            account.setFromName("System Notifications");
            account.setAuth(true);
            account.setStarttlsEnable(true);
            account.setDefault(true);
            account.setActive(true);
            repository.save(account);
        }
    }

    @Transactional(readOnly = true)
    public EmailAccount resolveAccount(String clientCode) {
        if (StringUtils.hasText(clientCode)) {
            return repository.findByClientCodeIgnoreCaseAndActiveTrue(clientCode.trim())
                    .orElseThrow(() -> new EntityNotFoundException("Active email account not found for client code: " + clientCode));
        }
        return repository.findByIsDefaultTrueAndActiveTrue()
                .or(() -> repository.findAll().stream().filter(EmailAccount::isActive).findFirst())
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
        if (account.isDefault()) {
            repository.findByIsDefaultTrueAndActiveTrue().ifPresent(existing -> {
                if (!existing.getId().equals(account.getId())) {
                    existing.setDefault(false);
                    repository.save(existing);
                }
            });
        }
        return repository.save(account);
    }

    @Transactional
    public void delete(Long id) {
        EmailAccount account = getById(id);
        repository.delete(account);
    }
}
