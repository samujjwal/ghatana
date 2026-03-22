/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Registry for looking up and managing {@link WorkflowDefinition} instances.
 *
 * <p>Implementations may be in-memory (for testing) or JDBC-backed (for production).
 * The registry supports versioning — callers can fetch a specific version or the
 * latest version of any workflow definition.
 *
 * @doc.type interface
 * @doc.purpose SPI for workflow definition persistence and lookup
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface WorkflowDefinitionRegistry {

    /**
     * Registers (or updates) a workflow definition.
     *
     * @param definition the definition to register
     * @return a promise that completes when the definition is persisted
     */
    Promise<Void> register(@NotNull WorkflowDefinition definition);

    /**
     * Looks up the latest version of a workflow definition.
     *
     * @param workflowId the workflow identifier
     * @return the latest definition, or empty if not found
     */
    Promise<Optional<WorkflowDefinition>> findLatest(@NotNull String workflowId);

    /**
     * Looks up a specific version of a workflow definition.
     *
     * @param workflowId the workflow identifier
     * @param version    the version number
     * @return the definition at that version, or empty if not found
     */
    Promise<Optional<WorkflowDefinition>> findByVersion(@NotNull String workflowId, int version);

    /**
     * Lists all registered workflow definitions (latest version only).
     *
     * @return all latest definitions
     */
    Promise<List<WorkflowDefinition>> listAll();

    /**
     * Removes a workflow definition by ID (all versions).
     *
     * @param workflowId the workflow identifier
     * @return a promise that completes when the definition is removed
     */
    Promise<Void> remove(@NotNull String workflowId);
}
