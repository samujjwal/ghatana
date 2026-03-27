/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.change;

/**
 * Classifies the kind of change being submitted for approval.
 *
 * @doc.type enum
 * @doc.purpose Classify change requests by their domain impact
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ChangeType {

    /** A governance or access-control policy is being added or modified. */
    POLICY_UPDATE,

    /** A new tool or integration is being registered or updated. */
    TOOL_REGISTRATION,

    /** A new permission grant or role assignment is being requested. */
    PERMISSION_GRANT,

    /** A runtime or infrastructure configuration value is changing. */
    CONFIG_CHANGE,

    /** A new agent definition or version is being deployed. */
    AGENT_DEPLOYMENT,

    /** A data schema is being created or migrated. */
    DATA_SCHEMA_CHANGE,

    /** A tenant-level feature flag or capability is being toggled. */
    FEATURE_FLAG
}
