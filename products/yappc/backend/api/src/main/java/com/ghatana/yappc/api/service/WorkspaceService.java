/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.Workspace;
import com.ghatana.yappc.api.domain.Workspace.MemberRole;
import com.ghatana.yappc.api.domain.Workspace.WorkspaceSettings;
import com.ghatana.yappc.api.domain.Workspace.WorkspaceMember;
import com.ghatana.yappc.api.domain.Workspace.WorkspaceStatus;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
import com.ghatana.yappc.api.workspace.dto.AddMemberRequest;
import com.ghatana.yappc.api.workspace.dto.CreateWorkspaceRequest;
import com.ghatana.yappc.api.workspace.dto.UpdateMemberRequest;
import com.ghatana.yappc.api.workspace.dto.UpdateSettingsRequest;
import com.ghatana.yappc.api.workspace.dto.UpdateWorkspaceRequest;
import com.ghatana.yappc.api.workspace.dto.WorkspaceMemberResponse;
import com.ghatana.yappc.api.workspace.dto.WorkspaceResponse;
import com.ghatana.yappc.api.workspace.dto.WorkspaceResponse.WorkspaceStats;
import com.ghatana.yappc.api.workspace.dto.WorkspaceSettingsResponse;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified application service for workspace management.
 *
 * @doc.type class
 * @doc.purpose Workspace management service (simplified)
 * @doc.layer application
 * @doc.pattern Service
 */
public class WorkspaceService {

  private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);

  private final WorkspaceRepository repository;
  private final AuditService auditService;

  public WorkspaceService(WorkspaceRepository repository, AuditService auditService) {
    this.repository = repository;
    this.auditService = auditService;
  }

  public Promise<WorkspaceResponse> createWorkspace(String tenantId, String ownerId, CreateWorkspaceRequest request) {
      Workspace workspace = new Workspace();
      workspace.setTenantId(tenantId);
      workspace.setName(request.name());
      workspace.setDescription(request.description());
      workspace.setOwnerId(ownerId);
      if (request.metadata() != null) {
          workspace.setMetadata(new HashMap<>(request.metadata()));
      }
      
      // Auto-add owner as admin/owner member
      WorkspaceMember owner = WorkspaceMember.create(ownerId, "unknown@email.com", "Owner", MemberRole.OWNER); // Email/Name would come from User Service ideally
      workspace.addMember(owner);

      return repository.save(workspace)
              .map(v -> mapToResponse(workspace));
  }

  public Promise<List<WorkspaceResponse>> listWorkspaces(String tenantId, String userId) {
      return repository.findByTenantId(tenantId)
          .map(list -> list.stream()
                  .filter(w -> isMember(w, userId) || w.getOwnerId().equals(userId)) // Basic filtering
                  .map(this::mapToResponse)
                  .toList());
  }
  
  public Promise<WorkspaceResponse> getWorkspace(String tenantId, String id) {
      return repository.findById(tenantId, UUID.fromString(id))
          .map(opt -> opt.map(this::mapToResponse).orElse(null));
  }

  public Promise<WorkspaceResponse> updateWorkspace(String tenantId, String id, UpdateWorkspaceRequest request) {
      return repository.findById(tenantId, UUID.fromString(id))
              .then(opt -> {
                  if (opt.isEmpty()) {
                      return Promise.of(null);
                  }
                  Workspace w = opt.get();
                  if (request.name() != null) w.setName(request.name()); 
                  if (request.description() != null) w.setDescription(request.description());
                  if (request.status() != null) {
                      try {
                          w.setStatus(WorkspaceStatus.valueOf(request.status()));
                      } catch (IllegalArgumentException e) {
                          // Ignore invalid status for now or log warning
                      }
                  }
                  if (request.metadata() != null) {
                      Map<String, Object> meta = w.getMetadata();
                      if (meta == null) meta = new HashMap<>(); // Ensure map exists
                      meta.putAll(request.metadata());
                      w.setMetadata(meta);
                  }
                  w.setUpdatedAt(Instant.now());
                  return repository.save(w).map(v -> mapToResponse(w));
              });
  }
  
  public Promise<Void> deleteWorkspace(String tenantId, String id) {
      return repository.delete(tenantId, UUID.fromString(id));
  }

  public Promise<List<WorkspaceMemberResponse>> listMembers(String tenantId, String workspaceId) {
      return repository.findById(tenantId, UUID.fromString(workspaceId))
          .map(opt -> opt
              .map(workspace -> workspace.getMembers().stream().map(this::mapMemberToResponse).toList())
              .orElse(List.of()));
  }

  public Promise<WorkspaceMemberResponse> addMember(
      String tenantId, String workspaceId, AddMemberRequest request) {
      return repository.findById(tenantId, UUID.fromString(workspaceId))
          .then(opt -> {
              if (opt.isEmpty()) {
                  return Promise.of(null);
              }

              Workspace workspace = opt.get();
              String normalizedRole =
                  request.role() != null ? request.role().trim().toUpperCase() : MemberRole.MEMBER.name();
              MemberRole role = MemberRole.valueOf(normalizedRole);

              WorkspaceMember member = WorkspaceMember.create(
                  "user-" + UUID.randomUUID(),
                  request.email(),
                  request.name() != null ? request.name() : request.email(),
                  role);
              member.setPersona(request.persona());
              workspace.addMember(member);
              workspace.setUpdatedAt(Instant.now());
              return repository.save(workspace).map(saved -> mapMemberToResponse(member));
          });
  }

  public Promise<WorkspaceMemberResponse> updateMember(
      String tenantId, String workspaceId, String userId, UpdateMemberRequest request) {
      return repository.findById(tenantId, UUID.fromString(workspaceId))
          .then(opt -> {
              if (opt.isEmpty()) {
                  return Promise.of(null);
              }

              Workspace workspace = opt.get();
              var memberOpt = workspace.findMember(userId);
              if (memberOpt.isEmpty()) {
                  return Promise.of(null);
              }

              WorkspaceMember member = memberOpt.get();
              if (request.role() != null && !request.role().isBlank()) {
                  MemberRole role = MemberRole.valueOf(request.role().trim().toUpperCase());
                  member.setRole(role);
              }
              if (request.persona() != null) {
                  member.setPersona(request.persona());
              }
              workspace.setUpdatedAt(Instant.now());
              return repository.save(workspace).map(saved -> mapMemberToResponse(member));
          });
  }

  public Promise<Boolean> removeMember(String tenantId, String workspaceId, String userId) {
      return repository.findById(tenantId, UUID.fromString(workspaceId))
          .then(opt -> {
              if (opt.isEmpty()) {
                  return Promise.of(false);
              }

              Workspace workspace = opt.get();
              if (workspace.isOwner(userId)) {
                  return Promise.ofException(new IllegalStateException("Cannot remove workspace owner"));
              }
              boolean removed = workspace.removeMember(userId);
              if (!removed) {
                  return Promise.of(false);
              }
              return repository.save(workspace).map(saved -> true);
          });
  }

  public Promise<WorkspaceSettingsResponse> getSettings(String tenantId, String workspaceId) {
      return repository.findById(tenantId, UUID.fromString(workspaceId))
          .map(opt -> opt.map(this::mapSettingsToResponse).orElse(null));
  }

  public Promise<WorkspaceSettingsResponse> updateSettings(
      String tenantId, String workspaceId, UpdateSettingsRequest request) {
      return repository.findById(tenantId, UUID.fromString(workspaceId))
          .then(opt -> {
              if (opt.isEmpty()) {
                  return Promise.of(null);
              }

              Workspace workspace = opt.get();
              WorkspaceSettings settings = workspace.getSettings();
              if (request.aiSuggestionsEnabled() != null) {
                  settings.setAiSuggestionsEnabled(request.aiSuggestionsEnabled());
              }
              if (request.autoVersioningEnabled() != null) {
                  settings.setAutoVersioningEnabled(request.autoVersioningEnabled());
              }
              if (request.requireApprovalForChanges() != null) {
                  settings.setRequireApprovalForChanges(request.requireApprovalForChanges());
              }
              if (request.defaultReviewers() != null) {
                  settings.setDefaultReviewers(request.defaultReviewers());
              }
              if (request.suggestionExpirationDays() != null) {
                  settings.setSuggestionExpirationDays(request.suggestionExpirationDays());
              }
              if (request.timezone() != null) {
                  settings.setTimezone(request.timezone());
              }
              if (request.language() != null) {
                  settings.setLanguage(request.language());
              }
              if (request.customSettings() != null) {
                  settings.setCustomSettings(new HashMap<>(request.customSettings()));
              }

              workspace.setSettings(settings);
              workspace.setUpdatedAt(Instant.now());
              return repository.save(workspace).map(saved -> mapSettingsToResponse(saved));
          });
  }
  
  private boolean isMember(Workspace w, String userId) {
      if (w.getMembers() == null) return false;
      return w.getMembers().stream().anyMatch(m -> m.getUserId().equals(userId));
  }
  
  private WorkspaceResponse mapToResponse(Workspace domain) {
      WorkspaceStats stats = new WorkspaceStats(
              domain.getMembers() != null ? domain.getMembers().size() : 0, 
              0, // teams
              0 // projects
      );
      
      Map<String, String> metaStr = new HashMap<>();
      if (domain.getMetadata() != null) {
          domain.getMetadata().forEach((k, v) -> metaStr.put(k, v != null ? v.toString() : null));
      }

      return new WorkspaceResponse(
          domain.getId().toString(),
          domain.getName(),
          domain.getDescription(),
          domain.getTenantId(),
          domain.getOwnerId(),
          domain.getStatus() != null ? domain.getStatus().name() : WorkspaceStatus.ACTIVE.name(),
          domain.getCreatedAt(),
          domain.getUpdatedAt(),
          0, // projectCount
          domain.getMembers() != null ? domain.getMembers().size() : 0,
          stats,
          metaStr
      );
  }

  private WorkspaceMemberResponse mapMemberToResponse(WorkspaceMember member) {
      return new WorkspaceMemberResponse(
          member.getUserId(),
          member.getEmail(),
          member.getName(),
          member.getRole() != null ? member.getRole().name() : MemberRole.MEMBER.name(),
          member.getPersona(),
          member.getJoinedAt(),
          member.getLastActiveAt(),
          member.getStatus() != null ? member.getStatus().name() : "ACTIVE");
  }

  private WorkspaceSettingsResponse mapSettingsToResponse(Workspace workspace) {
      WorkspaceSettings settings = workspace.getSettings() != null ? workspace.getSettings() : new WorkspaceSettings();
      return new WorkspaceSettingsResponse(
          workspace.getId().toString(),
          settings.isAiSuggestionsEnabled(),
          settings.isAutoVersioningEnabled(),
          settings.isRequireApprovalForChanges(),
          settings.getDefaultReviewers(),
          settings.getSuggestionExpirationDays(),
          settings.getTimezone(),
          settings.getLanguage(),
          settings.getCustomSettings() != null ? settings.getCustomSettings() : Map.of());
  }
}
