package com.jmcode.notification.security;

import com.jmcode.notification.common.InvalidCredentialsException;
import com.jmcode.notification.company.Company;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminUser create(String email, String rawPassword, Role role, Company company) {
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Admin user already exists for email: " + email);
        }
        requireConsistentScope(role, company);
        AdminUser user = new AdminUser();
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setCompany(company);
        user.setActive(true);
        return repository.save(user);
    }

    @Transactional(readOnly = true)
    public List<AdminUser> list(Long companyId) {
        return companyId == null ? repository.findAll() : repository.findAllByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public AdminUser getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Admin user not found with id=" + id));
    }

    @Transactional
    public AdminUser update(Long id, Role role, Company company, boolean active) {
        AdminUser user = getById(id);
        requireConsistentScope(role, company);
        user.setRole(role);
        user.setCompany(company);
        user.setActive(active);
        return repository.save(user);
    }

    @Transactional
    public void resetPassword(Long id, String rawPassword) {
        AdminUser user = getById(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        repository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getById(id));
    }

    /**
     * Credenciales inválidas se traducen en 401 (antes {@code EntityNotFoundException},
     * que el handler global convertía en un 404 engañoso). No se distingue entre usuario
     * inexistente, inactivo, con la empresa desactivada o password incorrecta.
     */
    @Transactional(readOnly = true)
    public AdminUser authenticate(String email, String rawPassword) {
        AdminUser user = repository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Desactivar la empresa deja fuera a sus administradores sin tocar cada usuario.
        if (user.getCompany() != null && !user.getCompany().isActive()) {
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
        return create(props.adminEmail(), props.adminPassword(), Role.SUPER_ADMIN, null);
    }

    /** El ámbito lo da la empresa: un ADMIN sin ella no vería nada, y un SUPER_ADMIN con ella dejaría de ser global. */
    private static void requireConsistentScope(Role role, Company company) {
        if (role == Role.ADMIN && company == null) {
            throw new IllegalArgumentException("An ADMIN user must belong to a company: companyId is required");
        }
        if (role == Role.SUPER_ADMIN && company != null) {
            throw new IllegalArgumentException("A SUPER_ADMIN user cannot be linked to a company");
        }
    }
}
