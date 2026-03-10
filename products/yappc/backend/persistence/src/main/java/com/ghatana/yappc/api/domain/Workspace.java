/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain entity representing a workspace.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates workspace attributes including settings, membership, and organizational hierarchy.
 *
 * <p><b>Workspace Hierarchy</b><br>
 *
 * <pre>
 * Tenant
 *   └── Workspace (this entity)
 *         ├── Projects
 *         ├── Members
 *         └── Teams
 * </pre>
 *
 * <p><b>Member Roles</b><br>
 * - OWNER: Full control, can delete workspace - ADMIN: Manage members, settings - MEMBER:
 * Create/edit content - VIEWER: Read-only access
 *
 * @doc.type class
 * @doc.purpose Workspace domain entity
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class Workspace {

  private UUID id;
  private String tenantId;
  private String name;
  private String description;
  private String ownerId;
  private WorkspaceStatus status;
  private WorkspaceSettings settings;
  private Instant createdAt;
  private Instant updatedAt;
  private List<WorkspaceMember> members;
  private List<Team> teams;
  private Map<String, Object> metadata;

  public Workspace() {
    this.id = UUID.randomUUID();
    this.status = WorkspaceStatus.ACTIVE;
    this.settings = new WorkspaceSettings();
    this.members = new ArrayList<>();
    this.teams = new ArrayList<>();
    this.metadata = new HashMap<>();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  // ========== Enums ==========

  public enum WorkspaceStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED,
    DELETED
  }

  public enum MemberRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER
  }

  public enum MemberStatus {
    ACTIVE,
    PENDING_INVITE,
    SUSPENDED,
    REMOVED
  }

  // ========== Nested Classes ==========

  /** Workspace-level settings. */
  public static class WorkspaceSettings {
    private boolean aiSuggestionsEnabled = true;
    private boolean autoVersioningEnabled = true;
    private boolean requireApprovalForChanges = true;
    private int defaultReviewers = 1;
    private int suggestionExpirationDays = 7;
    private String timezone = "UTC";
    private String language = "en";
    private Map<String, String> customSettings = new HashMap<>();

    // Getters and setters
    public boolean isAiSuggestionsEnabled() {
      return aiSuggestionsEnabled;
    }

    public void setAiSuggestionsEnabled(boolean val) {
      this.aiSuggestionsEnabled = val;
    }

    public boolean isAutoVersioningEnabled() {
      return autoVersioningEnabled;
    }

    public void setAutoVersioningEnabled(boolean val) {
      this.autoVersioningEnabled = val;
    }

    public boolean isRequireApprovalForChanges() {
      return requireApprovalForChanges;
    }

    public void setRequireApprovalForChanges(boolean val) {
      this.requireApprovalForChanges = val;
    }

    public int getDefaultReviewers() {
      return defaultReviewers;
    }

    public void setDefaultReviewers(int val) {
      this.defaultReviewers = val;
    }

    public int getSuggestionExpirationDays() {
      return suggestionExpirationDays;
    }

    public void setSuggestionExpirationDays(int val) {
      this.suggestionExpirationDays = val;
    }

    public String getTimezone() {
      return timezone;
    }

    public void setTimezone(String val) {
      this.timezone = val;
    }

    public String getLanguage() {
      return language;
    }

    public void setLanguage(String val) {
      this.language = val;
    }

    public Map<String, String> getCustomSettings() {
      return customSettings;
    }

    public void setCustomSettings(Map<String, String> val) {
      this.customSettings = val;
    }
  }

  /** Workspace member with role and persona. */
  public static class WorkspaceMember {
    private String userId;
    private String email;
    private String name;
    private MemberRole role;
    private String persona;
    private MemberStatus status;
    private Instant joinedAt;
    private Instant lastActiveAt;

    public WorkspaceMember() {
      this.status = MemberStatus.PENDING_INVITE;
      this.joinedAt = Instant.now();
    }

    // Builder pattern
    public static WorkspaceMember create(
        String userId, String email, String name, MemberRole role) {
      WorkspaceMember member = new WorkspaceMember();
      member.userId = userId;
      member.email = email;
      member.name = name;
      member.role = role;
      member.status = MemberStatus.ACTIVE;
      return member;
    }

    // Getters and setters
    public String getUserId() {
      return userId;
    }

    public void setUserId(String val) {
      this.userId = val;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String val) {
      this.email = val;
    }

    public String getName() {
      return name;
    }

    public void setName(String val) {
      this.name = val;
    }

    public MemberRole getRole() {
      return role;
    }

    public void setRole(MemberRole val) {
      this.role = val;
    }

    public String getPersona() {
      return persona;
    }

    public void setPersona(String val) {
      this.persona = val;
    }

    public MemberStatus getStatus() {
      return status;
    }

    public void setStatus(MemberStatus val) {
      this.status = val;
    }

    public Instant getJoinedAt() {
      return joinedAt;
    }

    public void setJoinedAt(Instant val) {
      this.joinedAt = val;
    }

    public Instant getLastActiveAt() {
      return lastActiveAt;
    }

    public void setLastActiveAt(Instant val) {
      this.lastActiveAt = val;
    }
  }

  /** Team within a workspace (supports hierarchy). */
  public static class Team {
    private UUID id;
    private String name;
    private String description;
    private UUID parentTeamId;
    private List<String> memberUserIds;
    private String leaderId;
    private Instant createdAt;

    public Team() {
      this.id = UUID.randomUUID();
      this.memberUserIds = new ArrayList<>();
      this.createdAt = Instant.now();
    }

    public static Team create(String name, String description, String leaderId) {
      Team team = new Team();
      team.name = name;
      team.description = description;
      team.leaderId = leaderId;
      if (leaderId != null) {
        team.memberUserIds.add(leaderId);
      }
      return team;
    }

    // Getters and setters
    public UUID getId() {
      return id;
    }

    public void setId(UUID val) {
      this.id = val;
    }

    public String getName() {
      return name;
    }

    public void setName(String val) {
      this.name = val;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String val) {
      this.description = val;
    }

    public UUID getParentTeamId() {
      return parentTeamId;
    }

    public void setParentTeamId(UUID val) {
      this.parentTeamId = val;
    }

    public List<String> getMemberUserIds() {
      return memberUserIds;
    }

    public void setMemberUserIds(List<String> val) {
      this.memberUserIds = val;
    }

    public String getLeaderId() {
      return leaderId;
    }

    public void setLeaderId(String val) {
      this.leaderId = val;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant val) {
      this.createdAt = val;
    }

    public void addMember(String userId) {
      if (!memberUserIds.contains(userId)) {
        memberUserIds.add(userId);
      }
    }

    public void removeMember(String userId) {
      memberUserIds.remove(userId);
    }
  }

  // ========== Domain Methods ==========

  /** Add a member to the workspace. */
  public void addMember(WorkspaceMember member) {
    // Check if already exists
    boolean exists = members.stream().anyMatch(m -> m.getUserId().equals(member.getUserId()));
    if (!exists) {
      members.add(member);
      this.updatedAt = Instant.now();
    }
  }

  /** Remove a member from the workspace. */
  public boolean removeMember(String userId) {
    boolean removed = members.removeIf(m -> m.getUserId().equals(userId));
    if (removed) {
      this.updatedAt = Instant.now();
    }
    return removed;
  }

  /** Find a member by user ID. */
  public Optional<WorkspaceMember> findMember(String userId) {
    return members.stream().filter(m -> m.getUserId().equals(userId)).findFirst();
  }

  /** Update a member's role. */
  public boolean updateMemberRole(String userId, MemberRole newRole) {
    return findMember(userId)
        .map(
            member -> {
              member.setRole(newRole);
              this.updatedAt = Instant.now();
              return true;
            })
        .orElse(false);
  }

  /** Update a member's persona. */
  public boolean updateMemberPersona(String userId, String persona) {
    return findMember(userId)
        .map(
            member -> {
              member.setPersona(persona);
              this.updatedAt = Instant.now();
              return true;
            })
        .orElse(false);
  }

  /** Add a team to the workspace. */
  public void addTeam(Team team) {
    teams.add(team);
    this.updatedAt = Instant.now();
  }

  /** Find a team by ID. */
  public Optional<Team> findTeam(UUID teamId) {
    return teams.stream().filter(t -> t.getId().equals(teamId)).findFirst();
  }

  /** Get child teams of a parent team. */
  public List<Team> getChildTeams(UUID parentTeamId) {
    return teams.stream().filter(t -> parentTeamId.equals(t.getParentTeamId())).toList();
  }

  /** Get root teams (no parent). */
  public List<Team> getRootTeams() {
    return teams.stream().filter(t -> t.getParentTeamId() == null).toList();
  }

  /** Check if user has specific role or higher. */
  public boolean hasRole(String userId, MemberRole requiredRole) {
    return findMember(userId)
        .map(m -> m.getRole().ordinal() <= requiredRole.ordinal())
        .orElse(false);
  }

  /** Check if user is owner. */
  public boolean isOwner(String userId) {
    return ownerId != null && ownerId.equals(userId);
  }

  // ========== Builder ==========

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Workspace workspace = new Workspace();

    public Builder tenantId(String tenantId) {
      workspace.tenantId = tenantId;
      return this;
    }

    public Builder name(String name) {
      workspace.name = name;
      return this;
    }

    public Builder description(String description) {
      workspace.description = description;
      return this;
    }

    public Builder ownerId(String ownerId) {
      workspace.ownerId = ownerId;
      return this;
    }

    public Builder status(WorkspaceStatus status) {
      workspace.status = status;
      return this;
    }

    public Builder settings(WorkspaceSettings settings) {
      workspace.settings = settings;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      workspace.metadata = metadata;
      return this;
    }

    public Workspace build() {
      Objects.requireNonNull(workspace.tenantId, "Tenant ID is required");
      Objects.requireNonNull(workspace.name, "Name is required");
      Objects.requireNonNull(workspace.ownerId, "Owner ID is required");

      // Add owner as first member
      WorkspaceMember owner =
          WorkspaceMember.create(
              workspace.ownerId,
              null, // email will be set later
              null, // name will be set later
              MemberRole.OWNER);
      workspace.members.add(owner);

      return workspace;
    }
  }

  // ========== Getters and Setters ==========

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
    this.updatedAt = Instant.now();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
    this.updatedAt = Instant.now();
  }

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(String ownerId) {
    this.ownerId = ownerId;
  }

  public WorkspaceStatus getStatus() {
    return status;
  }

  public void setStatus(WorkspaceStatus status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public WorkspaceSettings getSettings() {
    return settings;
  }

  public void setSettings(WorkspaceSettings settings) {
    this.settings = settings;
    this.updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<WorkspaceMember> getMembers() {
    return Collections.unmodifiableList(members);
  }

  public void setMembers(List<WorkspaceMember> members) {
    this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
  }

  public List<Team> getTeams() {
    return Collections.unmodifiableList(teams);
  }

  public void setTeams(List<Team> teams) {
    this.teams = teams != null ? new ArrayList<>(teams) : new ArrayList<>();
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public int getMemberCount() {
    return members.size();
  }

  public int getTeamCount() {
    return teams.size();
  }
}
