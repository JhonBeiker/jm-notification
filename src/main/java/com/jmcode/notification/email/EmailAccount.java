package com.jmcode.notification.email;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "email_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_account_client_code", columnNames = "client_code"),
        indexes = {
                @Index(name = "idx_email_account_active", columnList = "active"),
                @Index(name = "idx_email_account_is_default", columnList = "is_default"),
                @Index(name = "idx_email_account_company", columnList = "company_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class EmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Empresa propietaria. Sustituye a la antigua columna suelta {@code tenant_id}.
     * EAGER porque los DTO la leen fuera de la transacción ({@code open-in-view} está apagado).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "client_code", nullable = false, length = 64)
    private String clientCode;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private int port = 587;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "from_address", length = 255)
    private String fromAddress;

    @Column(name = "from_name", length = 128)
    private String fromName;

    @Column(name = "reply_to", length = 255)
    private String replyTo;

    @Column(name = "protocol", nullable = false, length = 16)
    private String protocol = "smtp";

    @Column(name = "auth", nullable = false)
    private boolean auth = true;

    @Column(name = "starttls_enable", nullable = false)
    private boolean starttlsEnable = true;

    @Column(name = "starttls_required", nullable = false)
    private boolean starttlsRequired = false;

    @Column(name = "ssl_enable", nullable = false)
    private boolean sslEnable = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "connection_timeout_ms", nullable = false)
    private int connectionTimeoutMs = 10000;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 10000;

    @Column(name = "write_timeout_ms", nullable = false)
    private int writeTimeoutMs = 10000;

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
