/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.domain;

import org.jetbrains.annotations.NotNull;

/**
 * Canonical PreviewSession contract for YAPPC.
 *
 * <p>This is the canonical contract for preview sessions across all YAPPC modules.
 * Preview sessions represent secure, time-limited access to generated artifacts.
 *
 * @doc.type class
 * @doc.purpose Canonical PreviewSession contract for YAPPC
 * @doc.layer domain
 * @doc.pattern Domain Model
 */
public final class PreviewSession {

    private final String sessionId;
    private final String tenantId;
    private final String workspaceId;
    private final String projectId;
    private final String artifactId;
    private final String actorId;
    private final long expiresAt;
    private final TrustLevel trustLevel;
    private final DataClassification dataClassification;
    private final boolean cspEnabled;
    private final boolean sandboxEnabled;
    private final long createdAt;

    public PreviewSession(
            @NotNull String sessionId,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId,
            @NotNull String actorId,
            long expiresAt,
            @NotNull TrustLevel trustLevel,
            @NotNull DataClassification dataClassification,
            boolean cspEnabled,
            boolean sandboxEnabled,
            long createdAt
    ) {
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.artifactId = artifactId;
        this.actorId = actorId;
        this.expiresAt = expiresAt;
        this.trustLevel = trustLevel;
        this.dataClassification = dataClassification;
        this.cspEnabled = cspEnabled;
        this.sandboxEnabled = sandboxEnabled;
        this.createdAt = createdAt;
    }

    public String sessionId() {
        return sessionId;
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

    public String artifactId() {
        return artifactId;
    }

    public String actorId() {
        return actorId;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public TrustLevel trustLevel() {
        return trustLevel;
    }

    public DataClassification dataClassification() {
        return dataClassification;
    }

    public boolean cspEnabled() {
        return cspEnabled;
    }

    public boolean sandboxEnabled() {
        return sandboxEnabled;
    }

    public long createdAt() {
        return createdAt;
    }

    /**
     * Trust level enumeration for preview artifacts.
     */
    public enum TrustLevel {
        TRUSTED,
        SEMI_TRUSTED,
        UNTRUSTED
    }

    /**
     * Data classification enumeration for preview content.
     */
    public enum DataClassification {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        RESTRICTED
    }
}
