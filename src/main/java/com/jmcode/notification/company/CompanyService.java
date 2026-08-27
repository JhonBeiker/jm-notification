package com.jmcode.notification.company;

import com.jmcode.notification.common.ConflictException;
import com.jmcode.notification.email.EmailAccountRepository;
import com.jmcode.notification.email.EmailTemplateRepository;
import com.jmcode.notification.security.AdminUserRepository;
import com.jmcode.notification.security.ApiClientRepository;
import com.jmcode.notification.telegram.TelegramBotAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository repository;
    private final AdminUserRepository adminUserRepository;
    private final ApiClientRepository apiClientRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final TelegramBotAccountRepository telegramBotAccountRepository;

    @Transactional(readOnly = true)
    public List<Company> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Company getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company not found with id=" + id));
    }

    @Transactional(readOnly = true)
    public Company getByCode(String code) {
        return repository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new EntityNotFoundException("Company not found with code=" + code));
    }

    @Transactional
    public Company save(Company company) {
        String code = company.getCode().trim().toLowerCase();
        repository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(company.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Company already exists for code: " + code);
                });
        company.setCode(code);
        return repository.save(company);
    }

    /**
     * Borrar una empresa con datos colgando dejaría usuarios sin ámbito y cuentas huérfanas:
     * se rechaza con 409 y se enumera qué falta desvincular.
     */
    @Transactional
    public void delete(Long id) {
        Company company = getById(id);
        long users = adminUserRepository.countByCompanyId(id);
        long emailAccounts = emailAccountRepository.countByCompanyId(id);
        long templates = emailTemplateRepository.countByCompanyId(id);
        long bots = telegramBotAccountRepository.countByCompanyId(id);
        long apiClients = apiClientRepository.countByCompanyId(id);
        if (users + emailAccounts + templates + bots + apiClients > 0) {
            throw new ConflictException(("Company '%s' still has %d admin user(s), %d email account(s), "
                    + "%d email template(s), %d telegram bot account(s) and %d api client(s). "
                    + "Delete them first or deactivate the company.")
                    .formatted(company.getCode(), users, emailAccounts, templates, bots, apiClients));
        }
        repository.delete(company);
    }
}
