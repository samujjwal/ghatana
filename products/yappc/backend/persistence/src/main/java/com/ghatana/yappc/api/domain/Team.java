/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a team.
 *
 * @doc.type class
 * @doc.purpose Team domain entity for collaboration
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class Team {

    private UUID id;
    private String tenantId;
    private String organizationId;
    private String name;
    private String description;
    private TeamType type;
    private TeamVisibility visibility;
    private String timezone;
    private WorkingHours workingHours;
    private List<TeamMember> members;
    private TeamSettings settings;
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> metadata;

    public Team() {
        this.id = UUID.randomUUID();
        this.members = new ArrayList<>();
        this.settings = new TeamSettings();
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ========== Enums ==========

    public enum TeamType {
        ENGINEERING,
        DESIGN,
        PRODUCT,
        OPERATIONS,
        SALES,
        CUSTOM
    }

    public enum TeamVisibility {
        PRIVATE,        // Only members
        ORGANIZATION,   // All org members
        PUBLIC          // Anyone (open source)
    }

    public enum MemberRole {
        OWNER,
        ADMIN,
        MEMBER,
        GUEST
    }

    // ========== Nested Classes ==========

    public static class TeamMember {
        private String userId;
        private String email;
        private String displayName;
        private MemberRole role;
        private Instant joinedAt;
        private Instant lastActiveAt;
        private Map<String, Object> preferences;

        public TeamMember() {
            this.preferences = new HashMap<>();
            this.joinedAt = Instant.now();
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public MemberRole getRole() { return role; }
        public void setRole(MemberRole role) { this.role = role; }

        public Instant getJoinedAt() { return joinedAt; }
        public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

        public Instant getLastActiveAt() { return lastActiveAt; }
        public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

        public Map<String, Object> getPreferences() { return preferences; }
        public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }
    }

    public static class WorkingHours {
        private TimeRange monday;
        private TimeRange tuesday;
        private TimeRange wednesday;
        private TimeRange thursday;
        private TimeRange friday;
        private TimeRange saturday;
        private TimeRange sunday;

        public TimeRange getMonday() { return monday; }
        public void setMonday(TimeRange monday) { this.monday = monday; }

        public TimeRange getTuesday() { return tuesday; }
        public void setTuesday(TimeRange tuesday) { this.tuesday = tuesday; }

        public TimeRange getWednesday() { return wednesday; }
        public void setWednesday(TimeRange wednesday) { this.wednesday = wednesday; }

        public TimeRange getThursday() { return thursday; }
        public void setThursday(TimeRange thursday) { this.thursday = thursday; }

        public TimeRange getFriday() { return friday; }
        public void setFriday(TimeRange friday) { this.friday = friday; }

        public TimeRange getSaturday() { return saturday; }
        public void setSaturday(TimeRange saturday) { this.saturday = saturday; }

        public TimeRange getSunday() { return sunday; }
        public void setSunday(TimeRange sunday) { this.sunday = sunday; }
    }

    public static class TimeRange {
        private String start;  // HH:mm format
        private String end;    // HH:mm format

        public String getStart() { return start; }
        public void setStart(String start) { this.start = start; }

        public String getEnd() { return end; }
        public void setEnd(String end) { this.end = end; }
    }

    public static class TeamSettings {
        private boolean allowGuestAccess = false;
        private boolean requireApproval = true;
        private int maxMembers = 100;
        private NotificationSettings notifications;

        public TeamSettings() {
            this.notifications = new NotificationSettings();
        }

        public boolean isAllowGuestAccess() { return allowGuestAccess; }
        public void setAllowGuestAccess(boolean allowGuestAccess) { this.allowGuestAccess = allowGuestAccess; }

        public boolean isRequireApproval() { return requireApproval; }
        public void setRequireApproval(boolean requireApproval) { this.requireApproval = requireApproval; }

        public int getMaxMembers() { return maxMembers; }
        public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }

        public NotificationSettings getNotifications() { return notifications; }
        public void setNotifications(NotificationSettings notifications) { this.notifications = notifications; }
    }

    public static class NotificationSettings {
        private boolean emailEnabled = true;
        private boolean slackEnabled = false;
        private boolean inAppEnabled = true;
        private String slackWebhookUrl;

        public boolean isEmailEnabled() { return emailEnabled; }
        public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }

        public boolean isSlackEnabled() { return slackEnabled; }
        public void setSlackEnabled(boolean slackEnabled) { this.slackEnabled = slackEnabled; }

        public boolean isInAppEnabled() { return inAppEnabled; }
        public void setInAppEnabled(boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }

        public String getSlackWebhookUrl() { return slackWebhookUrl; }
        public void setSlackWebhookUrl(String slackWebhookUrl) { this.slackWebhookUrl = slackWebhookUrl; }
    }

    // ========== Domain Methods ==========

    public void addMember(TeamMember member) {
        if (members.stream().noneMatch(m -> m.getUserId().equals(member.getUserId()))) {
            members.add(member);
            this.updatedAt = Instant.now();
        }
    }

    public void removeMember(String userId) {
        if (members.removeIf(m -> m.getUserId().equals(userId))) {
            this.updatedAt = Instant.now();
        }
    }

    public boolean hasMember(String userId) {
        return members.stream().anyMatch(m -> m.getUserId().equals(userId));
    }

    public TeamMember getMember(String userId) {
        return members.stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TeamType getType() { return type; }
    public void setType(TeamType type) { this.type = type; }

    public TeamVisibility getVisibility() { return visibility; }
    public void setVisibility(TeamVisibility visibility) { this.visibility = visibility; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public WorkingHours getWorkingHours() { return workingHours; }
    public void setWorkingHours(WorkingHours workingHours) { this.workingHours = workingHours; }

    public List<TeamMember> getMembers() { return members; }
    public void setMembers(List<TeamMember> members) { this.members = members; }

    public TeamSettings getSettings() { return settings; }
    public void setSettings(TeamSettings settings) { this.settings = settings; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Team)) return false;
        Team team = (Team) o;
        return Objects.equals(id, team.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
