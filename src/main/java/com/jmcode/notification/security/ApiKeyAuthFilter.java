package com.jmcode.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica por el header {@code X-Api-Key}: se compara el SHA-256 de la clave recibida
 * contra {@code api_key_hash}. La clave en claro nunca se almacena.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";
    private static final String API_CLIENT_AUTHORITY = "ROLE_API_CLIENT";

    private final ApiClientRepository repository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final ApiClientService apiClientService;

    public ApiKeyAuthFilter(ApiClientRepository repository, ApiKeyGenerator apiKeyGenerator,
                            ApiClientService apiClientService) {
        this.repository = repository;
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiClientService = apiClientService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null && !key.isBlank()) {
            repository.findByApiKeyHash(apiKeyGenerator.hash(key))
                    .filter(ApiClient::isActive)
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(ApiClient client) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                client, null, List.of(new SimpleGrantedAuthority(API_CLIENT_AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        apiClientService.touchLastUsed(client.getId());
    }
}
