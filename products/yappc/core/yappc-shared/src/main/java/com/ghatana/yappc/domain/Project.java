/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.domain;

import org.jetbrains.annotations.NotNull;

/**
 * Canonical Project contract for YAPPC.
 *
 * <p>This is the canonical contract for projects across all YAPPC modules.
 * All project-related operations should use this contract to ensure consistency
 * and avoid duplication across persistence stacks.
 *
 * @doc.type class
 * @doc.purpose Canonical Project contract for YAPPC
 * @doc.layer domain
 * @doc.pattern Domain Model
 */
public final class Project {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String name;
    private final String description;
    private final ProjectStatus status;
    private final long createdAt;
    private final long updatedAt;

    public Project(
            @NotNull String id,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String name,
            String description,
            @NotNull ProjectStatus status,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ProjectStatus status() {
        return status;
    }

    public long createdAt() {
        return createdAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    /**
     * Project status enumeration.
     */
    public enum ProjectStatus {
        ACTIVE,
        ARCHIVED,
        DELETED
    }
}
