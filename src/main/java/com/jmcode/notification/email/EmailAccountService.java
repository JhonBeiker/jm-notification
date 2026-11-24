package com.jmcode.notification.email;

import com.jmcode.notification.company.CompanyScope;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final CompanyScope companyScope;

    /**
     * Resolución de envío. La API Key acota a su empresa: un {@code clientCode} de otra
     * empresa se rechaza con 403 y, sin {@code clientCode}, la cuenta por defecto que se
     * busca es la de esa empresa, no la global.
     */
    @Transactional(readOnly = true)
    public EmailAccount resolveAccount(String clientCode) {
        if (StringUtils.hasText(clientCode)) {
            EmailAccount account = repository.findByClientCodeIgnoreCaseAndActiveTrue(clientCode.trim())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Active email account not found for client code: " + clientCode));
            companyScope.assertCanAccess(account.getCompany());
            return account;
        }
        Long companyId = companyScope.filterCompanyId();
        if (companyId != null) {
            return repository.findByCompanyIdAndIsDefaultTrueAndActiveTrue(companyId)
                    .or(() -> repository.findFirstByCompanyIdAndActiveTrueOrderByIdAsc(companyId))
                    .orElseThrow(() -> new EntityNotFoundException(
                            "No active email account configured for the company of this caller"));
        }
        return repository.findByIsDefaultTrueAndActiveTrue()
                .or(repository::findFirstByActiveTrueOrderByIdAsc)
                .orElseThrow(() -> new EntityNotFoundException("No active default email account configured in database"));
    }

    /** Administración: el ADMIN de una empresa sólo ve las cuentas de la suya. */
    @Transactional(readOnly = true)
    public List<EmailAccount> listAll() {
        Long companyId = companyScope.filterCompanyId();
        return companyId == null ? repository.findAll() : repository.findAllByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public EmailAccount getById(Long id) {
        EmailAccount account = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Email account not found with id=" + id));
        companyScope.assertCanAccess(account.getCompany());
        return account;
    }

    @Transactional
    public EmailAccount save(EmailAccount account, Long requestedCompanyId) {
        if (account.getId() == null && repository.existsByClientCodeIgnoreCase(account.getClientCode())) {
            throw new IllegalArgumentException("Email account already exists for clientCode: " + account.getClientCode());
        }
        account.setCompany(companyScope.resolveOwner(requestedCompanyId));
        if (account.isDefault() && !companyScope.unrestricted()) {
            // La cuenta por defecto es global (se usa cuando el envío no trae clientCode):
            // marcarla desde una empresa se la quitaría a otra.
            throw new AccessDeniedException("Only a SUPER_ADMIN can set the global default email account");
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
