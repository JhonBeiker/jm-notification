package com.jmcode.notification.dispatch.entity;

import com.jmcode.notification.channel.ChannelType;
import com.jmcode.notification.channel.NotificationStatus;
import com.jmcode.notification.company.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notification_company", columnList = "company_id"),
                @Index(name = "idx_notification_created_at", columnList = "created_at"),
                @Index(name = "idx_notification_company_created_at", columnList = "company_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Empresa propietaria del envío. EAGER porque los DTO la leen fuera de la transacción
     * ({@code open-in-view} está apagado).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "client_code", length = 64)
    private String clientCode;

    @Column(name = "template_name", length = 128)
    private String templateName;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
