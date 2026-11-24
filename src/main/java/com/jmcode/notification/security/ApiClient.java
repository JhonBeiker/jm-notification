package com.jmcode.notification.security;

import com.jmcode.notification.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "api_clients",
        indexes = {
                @Index(name = "idx_api_client_prefix", columnList = "api_key_prefix"),
                @Index(name = "idx_api_client_active", columnList = "active"),
                @Index(name = "idx_api_client_company", columnList = "company_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Empresa dueña de la clave: acota los envíos a las cuentas de esa empresa.
     * Sólo es {@code null} en claves anteriores a las empresas, que siguen sin acotar.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "contact_email", length = 191)
    private String contactEmail;

    @Column(name = "api_key_hash", nullable = false, length = 128)
    private String apiKeyHash;

    @Column(name = "api_key_prefix", nullable = false, length = 16)
    private String apiKeyPrefix;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(nullable = false)
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
