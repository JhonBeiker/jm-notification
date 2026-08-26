package com.jmcode.notification.security;

import com.jmcode.notification.common.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminUser create(String email, String rawPassword, Role role) {
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Admin user already exists for email: " + email);
        }
        AdminUser user = new AdminUser();
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setActive(true);
        return repository.save(user);
    }

    /**
     * Credenciales inválidas se traducen en 401 (antes {@code EntityNotFoundException},
     * que el handler global convertía en un 404 engañoso). No se distingue entre usuario
     * inexistente, inactivo o password incorrecta.
     */
    @Transactional(readOnly = true)
    public AdminUser authenticate(String email, String rawPassword) {
        AdminUser user = repository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    @Transactional
    public void markLoggedIn(Long id) {
        repository.findById(id).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            repository.save(user);
        });
    }

    @Transactional
    public AdminUser seedIfEmpty(AuthProperties props) {
        if (repository.count() > 0) {
            return null;
        }
        if (!StringUtils.hasText(props.adminEmail()) || !StringUtils.hasText(props.adminPassword())) {
            throw new IllegalStateException(
                    "Cannot seed super admin: set notification.auth.admin-email and notification.auth.admin-password env vars");
        }
        return create(props.adminEmail(), props.adminPassword(), Role.SUPER_ADMIN);
    }
}
