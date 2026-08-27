package com.jmcode.notification.email;

import com.jmcode.notification.company.Company;
import com.jmcode.notification.company.CompanyScope;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private static final String HTML = "html";
    private static final String TEXT = "text";

    private final EmailTemplateRepository repository;
    private final EmailTemplateRenderer renderer;
    private final CompanyScope companyScope;

    /** Administración: el ADMIN de una empresa sólo ve las plantillas de la suya. */
    @Transactional(readOnly = true)
    public List<EmailTemplate> listAll() {
        Long companyId = companyScope.filterCompanyId();
        return companyId == null ? repository.findAll() : repository.findAllByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public EmailTemplate getById(Long id) {
        EmailTemplate template = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Email template not found with id=" + id));
        companyScope.assertCanAccess(template.getCompany());
        return template;
    }

    @Transactional
    public EmailTemplate save(EmailTemplate template, Long requestedCompanyId) {
        Company company = companyScope.resolveOwner(requestedCompanyId);
        template.setCompany(company);
        requireUniqueName(template, company);
        if (!StringUtils.hasText(template.getContentType())) {
            // Sin contentType explícito se deduce del cuerpo, como hace el envío sin plantilla.
            template.setContentType(renderer.looksLikeHtml(template.getContent()) ? HTML : TEXT);
        }
        return repository.save(template);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getById(id));
    }

    /**
     * Plantilla que usará un envío: se busca dentro de la empresa dueña de la cuenta SMTP.
     * Las cuentas sin empresa (filas anteriores a esta tabla) usan las plantillas sin empresa.
     */
    @Transactional(readOnly = true)
    public EmailTemplate resolveForAccount(EmailAccount account, String name) {
        Company company = account.getCompany();
        Optional<EmailTemplate> found = company == null
                ? repository.findByCompanyIsNullAndNameIgnoreCase(name)
                : repository.findByCompanyIdAndNameIgnoreCase(company.getId(), name);
        return found.filter(EmailTemplate::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Active email template not found: " + name));
    }

    private void requireUniqueName(EmailTemplate template, Company company) {
        Optional<EmailTemplate> existing = company == null
                ? repository.findByCompanyIsNullAndNameIgnoreCase(template.getName())
                : repository.findByCompanyIdAndNameIgnoreCase(company.getId(), template.getName());
        existing.filter(other -> !other.getId().equals(template.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException(
                            "Email template '" + template.getName() + "' already exists for this company");
                });
    }
}
