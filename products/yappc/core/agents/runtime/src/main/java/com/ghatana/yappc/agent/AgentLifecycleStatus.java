package com.ghatana.yappc.agent;

/**
 * Lifecycle states for YAPPC agents in registry and health reporting.
 *
 * @doc.type enum
 * @doc.purpose Canonical lifecycle status for YAPPC agent health and registry monitoring
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum AgentLifecycleStatus {
    REGISTERED,
    INITIALIZING,
    READY,
    FAILED,
    STOPPING,
    STOPPED
}
