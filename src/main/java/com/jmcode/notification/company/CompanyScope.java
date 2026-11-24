package com.jmcode.notification.company;

import com.jmcode.notification.security.ApiClient;
import com.jmcode.notification.security.AuthPrincipal;
import com.jmcode.notification.security.Role;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resuelve a qué empresa puede tocar quien hace la petición.
 *
 * <p>Hay dos tipos de llamante acotado. Un {@link Role#ADMIN} sólo ve y modifica las filas
 * de su empresa (el {@link Role#SUPER_ADMIN} no está atado a ninguna y ve todo). Un
 * {@link ApiClient} sólo puede enviar por las cuentas de su empresa.
 *
 * <p>Sin autenticación (arranque de la app, webhook entrante de Telegram) no hay restricción:
 * esos caminos no llevan identidad de administrador ni clave de API.
 *
 * <p>Un ADMIN sin empresa asignada queda bloqueado en vez de heredar el acceso global: es el
 * caso de los usuarios creados antes de existir esta tabla. Una API Key sin empresa, en
 * cambio, sigue sin acotar por compatibilidad — el alta de claves nuevas ya la exige.
 */
@Component
@RequiredArgsConstructor
public class CompanyScope {

    private final CompanyRepository companyRepository;

    /** {@code true} si quien llama no está limitado a una empresa concreta. */
    public boolean unrestricted() {
        return scopedCompanyId() == null;
    }

    /** Id de la empresa a la que filtrar, o {@code null} si no hay filtro. */
    public Long filterCompanyId() {
        return scopedCompanyId();
    }

    /**
     * Empresa dueña de la fila que se va a crear o actualizar. El administrador de empresa
     * sólo puede escribir en la suya; el super admin elige (o deja la fila sin empresa).
     */
    public Company resolveOwner(Long requestedCompanyId) {
        Long own = scopedCompanyId();
        if (own == null) {
            return requestedCompanyId == null ? null : getById(requestedCompanyId);
        }
        if (requestedCompanyId != null && !requestedCompanyId.equals(own)) {
            throw new AccessDeniedException("Cannot assign records to another company");
        }
        return getById(own);
    }

    /** Corta el acceso a una fila de otra empresa (o sin empresa) para un llamante acotado. */
    public void assertCanAccess(Company owner) {
        Long own = scopedCompanyId();
        if (own == null) {
            return;
        }
        if (owner == null || !own.equals(owner.getId())) {
            throw new AccessDeniedException("Record belongs to another company");
        }
    }

    private Long scopedCompanyId() {
        Object principal = authenticatedPrincipal();
        if (principal instanceof AuthPrincipal admin) {
            return admin.role() == Role.SUPER_ADMIN ? null : requireCompany(admin.companyId());
        }
        if (principal instanceof ApiClient client) {
            return client.getCompany() == null ? null : client.getCompany().getId();
        }
        return null;
    }

    private static Object authenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }

    private static Long requireCompany(Long companyId) {
        if (companyId == null) {
            throw new AccessDeniedException("Admin user is not linked to any company");
        }
        return companyId;
    }

    private Company getById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company not found with id=" + id));
    }
}
