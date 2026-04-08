/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

/**
 * Describes the origin from which an {@link AgentPackage} was loaded.
 *
 * @doc.type enum
 * @doc.purpose Provenance taxonomy for agent packages
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum AgentPackageSource {
    /** Bundled with the platform; always trusted. */
    BUILT_IN,
    /** Loaded from a local filesystem path (development / testing). */
    LOCAL_FILE,
    /** Fetched from a remote artifact registry. */
    REMOTE_REGISTRY,
    /** Dynamically generated and registered at runtime. */
    DYNAMIC
}
