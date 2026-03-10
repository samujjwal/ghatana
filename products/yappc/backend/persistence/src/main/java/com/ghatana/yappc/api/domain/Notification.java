/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a notification.
 *
 * @doc.type class
 * @doc.purpose Notification domain entity for collaboration
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class Notification {

    private UUID id;
    private String tenantId;
    private String userId;
    private NotificationType type;
    private NotificationCategory category;
    private String title;
    private String message;
    private String actionUrl;
    private NotificationPriority priority;
    private boolean read;
    private boolean archived;
    private String sourceType;      // project, story, review, team, etc.
    private String sourceId;
    private String actorId;         // who triggered the notification
    private String actorName;
    private Instant createdAt;
    private Instant readAt;
    private Instant expiresAt;
    private Map<String, Object> metadata;

    public Notification() {
        this.id = UUID.randomUUID();
        this.priority = NotificationPriority.NORMAL;
        this.read = false;
        this.archived = false;
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
    }

    // ========== Enums ==========

    public enum NotificationType {
        // Project & Story
        STORY_ASSIGNED,
        STORY_UPDATED,
        STORY_COMPLETED,
        STORY_BLOCKED,
        SPRINT_STARTED,
        SPRINT_ENDING,
        SPRINT_COMPLETED,
        
        // Code Review
        REVIEW_REQUESTED,
        REVIEW_APPROVED,
        REVIEW_CHANGES_REQUESTED,
        REVIEW_COMMENT,
        REVIEW_MENTION,
        PR_MERGED,
        
        // Team
        TEAM_INVITATION,
        TEAM_MEMBER_JOINED,
        TEAM_MEMBER_LEFT,
        
        // General
        MENTION,
        COMMENT,
        REMINDER,
        SYSTEM,
        ALERT
    }

    public enum NotificationCategory {
        ASSIGNMENT,
        UPDATE,
        REVIEW,
        MENTION,
        TEAM,
        SYSTEM
    }

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    // ========== Domain Methods ==========

    public void markAsRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = Instant.now();
        }
    }

    public void markAsUnread() {
        this.read = false;
        this.readAt = null;
    }

    public void archive() {
        this.archived = true;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public NotificationCategory getCategory() { return category; }
    public void setCategory(NotificationCategory category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public NotificationPriority getPriority() { return priority; }
    public void setPriority(NotificationPriority priority) { this.priority = priority; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification)) return false;
        Notification that = (Notification) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
