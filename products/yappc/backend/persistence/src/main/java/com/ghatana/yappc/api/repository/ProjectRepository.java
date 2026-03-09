/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.Project;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Project persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Project repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface ProjectRepository {

    /** Save a project. */
    Promise<Project> save(Project project);

    /** Find a project by ID. */
    Promise<Optional<Project>> findById(UUID workspaceId, UUID id);

    /** Find a project by key. */
    Promise<Optional<Project>> findByKey(UUID workspaceId, String key);

    /** Find projects by workspace. */
    Promise<List<Project>> findByWorkspace(UUID workspaceId);

    /** Find active (non-archived) projects by workspace. */
    Promise<List<Project>> findActiveByWorkspace(UUID workspaceId);

    /** Search projects by name. */
    Promise<List<Project>> searchByName(UUID workspaceId, String query);

    /** Delete a project. */
    Promise<Boolean> delete(UUID workspaceId, UUID id);

    /** Check if a project exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);

    /** Check if a project key is available. */
    Promise<Boolean> isKeyAvailable(UUID workspaceId, String key);

    /** Count projects by workspace. */
    Promise<Long> countByWorkspace(UUID workspaceId);
}
