package com.jmcode.notification.whatsapp;

import com.jmcode.notification.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Dispositivo de WhatsApp en una instancia GOWA, por cliente y empresa. Equivale a la cuenta
 * de bot de Telegram: es la fila que resuelve un envío por {@code clientCode}.
 *
 * <p>{@code deviceId} es el UUID que genera GOWA al dar de alta el dispositivo y el que viaja
 * en la cabecera {@code X-Device-Id}; el emparejamiento (QR o pair code) vive allí, aquí sólo
 * se guarda a qué dispositivo apunta cada cliente y con qué credenciales se habla con GOWA.
 */
@Entity
@Table(
        name = "whatsapp_devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_whatsapp_device_client_code", columnNames = "client_code"),
                @UniqueConstraint(name = "uk_whatsapp_device_device_id", columnNames = "device_id")
        },
        indexes = {
                @Index(name = "idx_whatsapp_device_active", columnList = "active"),
                @Index(name = "idx_whatsapp_device_is_default", columnList = "is_default"),
                @Index(name = "idx_whatsapp_device_company", columnList = "company_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa propietaria. Un envío con el {@code clientCode} de otra empresa da 403. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "client_code", nullable = false, length = 64)
    private String clientCode;

    /**
     * Id del dispositivo en GOWA (lo genera GOWA, no se elige). Es nulo entre que se guarda la
     * fila y se provisiona el dispositivo, cosa que sólo pasa si GOWA no responde en el alta.
     */
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "name", length = 128)
    private String name;

    /** Base URL de la instancia GOWA. Vacío = la global {@code notification.whatsapp.api-url}. */
    @Column(name = "api_url", length = 255)
    private String apiUrl;

    /** Usuario de Basic Auth. Vacío = el global {@code notification.whatsapp.basic-auth-user}. */
    @Column(name = "basic_auth_user", length = 128)
    private String basicAuthUser;

    /**
     * Password de Basic Auth, cifrada con {@code PasswordEncryptor} (prefijo {@code enc:v1:}).
     * Vacío = la global {@code notification.whatsapp.basic-auth-password}.
     */
    @Column(name = "basic_auth_password", length = 512)
    private String basicAuthPassword;

    /** Número emparejado, tal y como lo reporta GOWA. Informativo: lo actualiza el status. */
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    /** Último estado conocido en GOWA ({@code disconnected}, {@code logged_in}, ...). */
    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

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
