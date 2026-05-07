package com.ghatana.digitalmarketing.domain.funnel;

/**
 * Lifecycle status for demo workspaces.
 *
 * @doc.type enum
 * @doc.purpose Demo workspace lifecycle states (P3-001)
 * @doc.layer product
 * @doc.pattern StateMachine
 */
public enum DemoWorkspaceStatus {
    /**
     * Workspace is provisioned but not yet activated.
     */
    PROVISIONED,

    /**
     * Workspace is active and ready for use.
     */
    ACTIVE,

    /**
     * Workspace has been deactivated by user or admin.
     */
    DEACTIVATED,

    /**
     * Workspace trial period has expired.
     */
    EXPIRED
}
