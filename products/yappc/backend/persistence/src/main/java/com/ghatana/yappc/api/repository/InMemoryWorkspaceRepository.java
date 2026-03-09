/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Workspace;
import io.activej.promise.Promise;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of WorkspaceRepository.
 *
 * <p><b>Purpose</b><br>
 * Provides a thread-safe in-memory storage for workspaces during development and testing. Data is
 * not persisted across application restarts.
 *
 * <p><b>Thread Safety</b><br>
 * Uses ConcurrentHashMap for thread-safe operations.
 *
 * @doc.type class
 * @doc.purpose In-memory workspace repository
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class InMemoryWorkspaceRepository implements WorkspaceRepository {

  private static final Logger logger = LoggerFactory.getLogger(InMemoryWorkspaceRepository.class);

  // Keyed by tenantId:workspaceId
  private final Map<String, Workspace> storage = new ConcurrentHashMap<>();

  private String key(String tenantId, UUID id) {
    return tenantId + ":" + id.toString();
  }

  @Override
  public Promise<Workspace> save(Workspace workspace) {
    Objects.requireNonNull(workspace, "Workspace must not be null");
    Objects.requireNonNull(workspace.getTenantId(), "Tenant ID must not be null");
    Objects.requireNonNull(workspace.getId(), "Workspace ID must not be null");

    String key = key(workspace.getTenantId(), workspace.getId());
    storage.put(key, workspace);
    logger.debug("Saved workspace: {} for tenant: {}", workspace.getId(), workspace.getTenantId());
    return Promise.of(workspace);
  }

  @Override
  public Promise<Optional<Workspace>> findById(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Workspace ID must not be null");

    String key = key(tenantId, id);
    Workspace workspace = storage.get(key);
    return Promise.of(Optional.ofNullable(workspace));
  }

  @Override
  public Promise<List<Workspace>> findByTenantId(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");

    List<Workspace> workspaces =
        storage.values().stream()
            .filter(ws -> tenantId.equals(ws.getTenantId()))
            .collect(Collectors.toList());
    return Promise.of(workspaces);
  }

  @Override
  public Promise<List<Workspace>> findByMemberUserId(String tenantId, String userId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(userId, "User ID must not be null");

    List<Workspace> workspaces =
        storage.values().stream()
            .filter(ws -> tenantId.equals(ws.getTenantId()))
            .filter(ws -> ws.getMembers().stream().anyMatch(m -> userId.equals(m.getUserId())))
            .collect(Collectors.toList());
    return Promise.of(workspaces);
  }

  @Override
  public Promise<List<Workspace>> findByOwnerId(String tenantId, String ownerId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(ownerId, "Owner ID must not be null");

    List<Workspace> workspaces =
        storage.values().stream()
            .filter(ws -> tenantId.equals(ws.getTenantId()))
            .filter(ws -> ownerId.equals(ws.getOwnerId()))
            .collect(Collectors.toList());
    return Promise.of(workspaces);
  }

  @Override
  public Promise<Void> delete(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Workspace ID must not be null");

    String key = key(tenantId, id);
    Workspace removed = storage.remove(key);
    if (removed != null) {
      logger.debug("Deleted workspace: {} from tenant: {}", id, tenantId);
    }
    return Promise.complete();
  }

  @Override
  public Promise<Boolean> exists(String tenantId, UUID id) {
    String key = key(tenantId, id);
    return Promise.of(storage.containsKey(key));
  }

  @Override
  public Promise<Long> count(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");

    long count = storage.values().stream().filter(ws -> tenantId.equals(ws.getTenantId())).count();
    return Promise.of(count);
  }

  /** Clear all data (for testing). */
  public void clear() {
    storage.clear();
  }

  /** Get count of all workspaces (for testing/metrics). */
  public int size() {
    return storage.size();
  }
}
