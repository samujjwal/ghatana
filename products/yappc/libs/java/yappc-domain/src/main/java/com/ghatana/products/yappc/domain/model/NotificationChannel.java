package com.ghatana.products.yappc.domain.model;

import com.ghatana.products.yappc.domain.enums.NotificationChannelType;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notification channel entity representing notification delivery endpoints.
 *
 * <p>
 * <b>Purpose</b><br>
 * NotificationChannel defines endpoints for delivering security alerts and
 * system notifications across multiple channels (email, Slack, webhooks, etc.).
 *
 * <p>
 * <b>Channel Configuration</b><br>
 * config JSONB stores channel-specific settings: - EMAIL: SMTP settings,
 * recipient addresses - SLACK: Webhook URL, channel ID, bot token - TEAMS:
 * Webhook URL, team/channel IDs - WEBHOOK: URL, headers, authentication - SMS:
 * Provider API key, phone numbers - PAGERDUTY: Integration key, routing key
 *
 * <p>
 * <b>Multi-Channel Notifications</b><br>
 * Alerts can be sent to multiple channels simultaneously based on severity: -
 * CRITICAL alerts → PagerDuty + Slack + Email - HIGH alerts → Slack + Email -
 * MEDIUM/LOW alerts → Email only
 *
 * @see NotificationRule
 * @see NotificationAlert
 * @doc.type class
 * @doc.purpose Notification channel configuration entity
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "notification_channel", indexes = {
    @Index(name = "idx_notification_channel_workspace_type",
            columnList = "workspace_id, channel_type"),
    @Index(name = "idx_notification_channel_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationChannel {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 50)
    private NotificationChannelType channelType;

    @Type(JsonBinaryType.class)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
