/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.domain;

import org.jetbrains.annotations.NotNull;

/**
 * Canonical GeneratedArtifact contract for YAPPC.
 *
 * <p>This is the canonical contract for generated artifacts across all YAPPC modules.
 * Generated artifacts are outputs from AI generation phases with provenance tracking.
 *
 * @doc.type class
 * @doc.purpose Canonical GeneratedArtifact contract for YAPPC
 * @doc.layer domain
 * @doc.pattern Domain Model
 */
public final class GeneratedArtifact {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String projectId;
    private final String specRef;
    private final String generatorVersion;
    private final String generatorModel;
    private final ArtifactType type;
    private final String contentHash;
    private final long createdAt;
    private final long updatedAt;

    public GeneratedArtifact(
            @NotNull String id,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String specRef,
            @NotNull String generatorVersion,
            @NotNull String generatorModel,
            @NotNull ArtifactType type,
            @NotNull String contentHash,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.specRef = specRef;
        this.generatorVersion = generatorVersion;
        this.generatorModel = generatorModel;
        this.type = type;
        this.contentHash = contentHash;
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

    public String generatorVersion() {
        return generatorVersion;
    }

    public String generatorModel() {
        return generatorModel;
    }

    public ArtifactType type() {
        return type;
    }

    public String contentHash() {
        return contentHash;
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
}
