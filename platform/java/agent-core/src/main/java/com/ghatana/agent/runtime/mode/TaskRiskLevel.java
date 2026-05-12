/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

/**
 * Risk level classification for a task.
 *
 * @doc.type enum
 * @doc.purpose Risk level classification for tasks
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum TaskRiskLevel {
    /**
     * Low risk - safe to execute with minimal safeguards.
     */
    LOW,

    /**
     * Medium risk - requires standard safeguards.
     */
    MEDIUM,

    /**
     * High risk - requires enhanced safeguards and possibly human approval.
     */
    HIGH,

    /**
     * Critical risk - requires human approval and strict safeguards.
     */
    CRITICAL
}
