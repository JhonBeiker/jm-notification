package com.jmcode.notification.security;

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

    @Transactional(readOnly = true)
    public List<ApiClient> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public ApiClient getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ApiClient not found with id=" + id));
    }

    @Transactional
    public CreatedApiClient create(String name, String contactEmail) {
        if (repository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("ApiClient already exists for name: " + name);
        }
        ApiKeyGenerator.GeneratedApiKey generated = apiKeyGenerator.generate();
        ApiClient client = new ApiClient();
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
