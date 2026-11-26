package com.jmcode.notification.security.entity;

import com.jmcode.notification.security.model.Role;
import jakarta.persistence.Column;
import com.jmcode.notification.security.model.Role;
import com.jmcode.notification.company.entity.Company;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.Entity;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.EnumType;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.FetchType;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.JoinColumn;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.ManyToOne;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.Enumerated;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.GeneratedValue;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.GenerationType;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.Id;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.PrePersist;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.PreUpdate;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.Table;
import com.jmcode.notification.security.model.Role;
import jakarta.persistence.UniqueConstraint;
import com.jmcode.notification.security.model.Role;
import lombok.Getter;
import com.jmcode.notification.security.model.Role;
import lombok.NoArgsConstructor;
import com.jmcode.notification.security.model.Role;
import lombok.Setter;

import com.jmcode.notification.security.model.Role;
import java.time.Instant;

@Entity
@Table(
        name = "admin_users",
        uniqueConstraints = @UniqueConstraint(name = "uk_admin_user_email", columnNames = "email")
)
@Getter
@Setter
@NoArgsConstructor
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 191)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * Empresa a la que pertenece el administrador. {@code null} sólo para SUPER_ADMIN,
     * que no está atado a ninguna. EAGER a propósito: el login y los DTO leen la empresa
     * fuera de la transacción y {@code open-in-view} está desactivado.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
