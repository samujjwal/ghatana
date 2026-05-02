package com.ghatana.digitalmarketing.domain.workspace;

/**
 * Lifecycle states of a DMOS workspace.
 *
 * @doc.type enum
 * @doc.purpose Workspace lifecycle state enumeration
 * @doc.layer product
 * @doc.pattern StatePattern
 */
public enum WorkspaceStatus {

    /** Workspace is active and fully operational. */
    ACTIVE,

    /** Workspace has been suspended (e.g. billing, compliance hold). */
    SUSPENDED,

    /** Workspace has been soft-deleted; data retained for audit purposes. */
    ARCHIVED
}
