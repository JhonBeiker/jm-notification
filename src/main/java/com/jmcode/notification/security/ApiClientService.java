package com.jmcode.notification.security;

import com.jmcode.notification.company.Company;
import com.jmcode.notification.company.CompanyScope;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiClientService {

    private final ApiClientRepository repository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final CompanyScope companyScope;

    /** El ADMIN de una empresa sólo ve sus claves; el filtro que pida se ignora. */
    @Transactional(readOnly = true)
    public List<ApiClient> list(Long requestedCompanyId) {
        Long scoped = companyScope.filterCompanyId();
        if (scoped != null) {
            return repository.findAllByCompanyId(scoped);
        }
        return requestedCompanyId == null ? repository.findAll() : repository.findAllByCompanyId(requestedCompanyId);
    }

    @Transactional(readOnly = true)
    public ApiClient getById(Long id) {
        ApiClient client = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ApiClient not found with id=" + id));
        companyScope.assertCanAccess(client.getCompany());
        return client;
    }

    /**
     * La clave siempre nace atada a una empresa: sin ella podría enviar por cualquier cuenta.
     * El ADMIN de una empresa recibe la suya de {@link CompanyScope}; el SUPER_ADMIN la indica.
     */
    @Transactional
    public CreatedApiClient create(String name, String contactEmail, Long requestedCompanyId) {
        Company company = companyScope.resolveOwner(requestedCompanyId);
        if (company == null) {
            throw new IllegalArgumentException("companyId is required: an API key must belong to a company");
        }
        if (repository.existsByCompanyIdAndNameIgnoreCase(company.getId(), name)) {
            throw new IllegalArgumentException("ApiClient already exists for name: " + name);
        }
        ApiKeyGenerator.GeneratedApiKey generated = apiKeyGenerator.generate();
        ApiClient client = new ApiClient();
        client.setCompany(company);
        client.setName(name.trim());
        client.setContactEmail(contactEmail == null ? null : contactEmail.trim().toLowerCase());
        client.setApiKeyHash(generated.hash());
        client.setApiKeyPrefix(generated.prefix());
        client.setActive(true);
        ApiClient saved = repository.save(client);
        return new CreatedApiClient(saved, generated.plainKey());
    }

    @Transactional
    public String rotateKey(Long id) {
        ApiClient client = getById(id);
        ApiKeyGenerator.GeneratedApiKey generated = apiKeyGenerator.generate();
        client.setApiKeyHash(generated.hash());
        client.setApiKeyPrefix(generated.prefix());
        client.setLastUsedAt(null);
        repository.save(client);
        return generated.plainKey();
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getById(id));
    }

    @Transactional
    public void touchLastUsed(Long id) {
        repository.touchLastUsed(id, Instant.now());
    }

    public record CreatedApiClient(ApiClient client, String plainKey) {
    }
}
