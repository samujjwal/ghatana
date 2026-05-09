/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.domain;

import org.jetbrains.annotations.NotNull;

/**
 * Canonical Artifact contract for YAPPC.
 *
 * <p>This is the canonical contract for artifacts across all YAPPC modules.
 * Artifacts represent generated code, documents, or other outputs from the
 * lifecycle phases.
 *
 * @doc.type class
 * @doc.purpose Canonical Artifact contract for YAPPC
 * @doc.layer domain
 * @doc.pattern Domain Model
 */
public final class Artifact {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String projectId;
    private final String specRef;
    private final ArtifactType type;
    private final String generatorVersion;
    private final ArtifactStatus status;
    private final long createdAt;
    private final long updatedAt;

    public Artifact(
            @NotNull String id,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String specRef,
            @NotNull ArtifactType type,
            @NotNull String generatorVersion,
            @NotNull ArtifactStatus status,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.specRef = specRef;
        this.type = type;
        this.generatorVersion = generatorVersion;
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

    public String projectId() {
        return projectId;
    }

    public String specRef() {
        return specRef;
    }

    public ArtifactType type() {
        return type;
    }

    public String generatorVersion() {
        return generatorVersion;
    }

    public ArtifactStatus status() {
        return status;
    }

    public long createdAt() {
        return createdAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    /**
     * Artifact type enumeration.
     */
    public enum ArtifactType {
        CODE,
        DOCUMENT,
        CONFIG,
        TEST,
        DEPLOYMENT_MANIFEST
    }

    /**
     * Artifact status enumeration.
     */
    public enum ArtifactStatus {
        GENERATED,
        REVIEWED,
        APPROVED,
        REJECTED,
        APPLIED,
        ROLLED_BACK
    }
}
