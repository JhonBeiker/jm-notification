package com.jmcode.notification.telegram;

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

@Entity
@Table(
        name = "telegram_bot_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_telegram_bot_client_code", columnNames = "client_code"),
        indexes = {
                @Index(name = "idx_telegram_bot_active", columnList = "active"),
                @Index(name = "idx_telegram_bot_is_default", columnList = "is_default")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TelegramBotAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "client_code", nullable = false, length = 64)
    private String clientCode;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "bot_token", nullable = false, length = 255)
    private String botToken;

    @Column(name = "api_url", nullable = false, length = 255)
    private String apiUrl = "https://api.telegram.org";

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "webhook_url", length = 512)
    private String webhookUrl;

    @Column(name = "webhook_auto_register", nullable = false)
    private boolean webhookAutoRegister = false;

    @Column(name = "require_active_subscriber", nullable = false)
    private boolean requireActiveSubscriber = true;

    @Column(name = "welcome_message", length = 512)
    private String welcomeMessage = "Suscripcion activa. Ya puedes recibir notificaciones.";

    @Column(name = "goodbye_message", length = 512)
    private String goodbyeMessage = "Suscripcion cancelada. Usa /start para volver a activarla.";

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