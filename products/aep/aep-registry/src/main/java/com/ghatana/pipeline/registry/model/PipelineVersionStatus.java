/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.model;

/**
 * Lifecycle status for a pipeline version.
 *
 * <p>Pipelines follow a simple state machine:
 * <pre>
 *   DRAFT ──publish──▶ PUBLISHED ──(new publish)──▶ ARCHIVED
 *     ▲                      │
 *     └──────rollback────────┘
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Define pipeline version lifecycle states (DRAFT → PUBLISHED → ARCHIVED)
 * @doc.layer product
 * @doc.pattern Enum
 */
public enum PipelineVersionStatus {

    /**
     * The pipeline is being actively edited and has not been formally released.
     * Drafts can receive unlimited edits without creating new version snapshots.
     */
    DRAFT,

    /**
     * The pipeline version has been formally published under a named label
     * (e.g., "v1.0.0"). At most one version per pipeline is PUBLISHED at a time;
     * publishing a new version archives the previous one.
     */
    PUBLISHED,

    /**
     * The pipeline version has been superseded by a newer PUBLISHED version.
     * Archived versions remain available for rollback but are not actively used.
     */
    ARCHIVED
}
