package com.jmcode.notification.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Empresa cliente. Es el ámbito (tenant) al que pertenecen los usuarios administradores,
 * las cuentas SMTP, los bots de Telegram y las plantillas de correo.
 *
 * <p>Sustituye a la antigua columna suelta {@code tenant_id}, que era un texto libre sin
 * tabla propia: no había forma de listar empresas ni de atar un administrador a una.
 */
@Entity
@Table(
        name = "companies",
        uniqueConstraints = @UniqueConstraint(name = "uk_company_code", columnNames = "code"),
        indexes = @Index(name = "idx_company_active", columnList = "active")
)
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador estable de negocio; se normaliza a minúsculas al guardar. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 191)
    private String name;

    @Column(name = "tax_id", length = 64)
    private String taxId;

    @Column(name = "contact_email", length = 191)
    private String contactEmail;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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
