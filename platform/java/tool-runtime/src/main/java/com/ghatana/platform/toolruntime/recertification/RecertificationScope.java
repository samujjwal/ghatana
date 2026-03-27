/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

/**
 * Defines what objects are included in a recertification campaign.
 *
 * @doc.type enum
 * @doc.purpose Classify the scope of a recertification campaign
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum RecertificationScope {

    /** All registered agents and their capability grants. */
    AGENT_PERMISSIONS,

    /** All registered tool integrations and their security classifications. */
    TOOL_REGISTRATIONS,

    /** Active governance and access-control policies. */
    POLICIES,

    /** Active data access consents and purpose-limitation grants. */
    DATA_ACCESS_CONSENTS,

    /** All of the above — full tenant governance recertification. */
    FULL
}
